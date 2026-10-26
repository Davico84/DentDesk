package com.odontorisas.service.staff;

import com.odontorisas.common.ClinicalCapacityState;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.persistence.entity.Membership;
import com.odontorisas.persistence.entity.ProfessionalCredential;
import com.odontorisas.persistence.repository.MembershipRepository;
import com.odontorisas.persistence.repository.ProfessionalCredentialRepository;
import com.odontorisas.persistence.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Capacidad clínica efectiva: una sola fuente para las dos firmas, la consulta del propio usuario y la
 * columna de Personal ({@code update-staff-professional-credential} design.md D3/D4).
 *
 * <p>Capacidad en la clínica activa = membresía {@code ACTIVE} + sin retiro + credencial declarada +
 * "habilitado hasta" no vencido. La vigencia se evalúa siempre en {@link #PERU}: la fecha la fija el
 * COP, que es peruano, así que la misma credencial vence igual en todas las clínicas, sea cual sea su
 * zona horaria.
 */
@Service
public class ClinicalCapacityService {

    /** Zona de la fecha "habilitado hasta" (design.md D3). No es la de la clínica, a propósito. */
    public static final ZoneId PERU = ZoneId.of("America/Lima");

    private final MembershipRepository memberships;
    private final ProfessionalCredentialRepository credentials;
    private final UserRepository users;
    private final Clock clock;

    public ClinicalCapacityService(MembershipRepository memberships, ProfessionalCredentialRepository credentials,
                                   UserRepository users, Clock clock) {
        this.memberships = memberships;
        this.credentials = credentials;
        this.users = users;
        this.clock = clock;
    }

    /** Fecha de hoy en Perú, la que decide la vigencia y la validación de "no anterior a hoy". */
    public LocalDate todayInPeru() {
        return LocalDate.now(clock.withZone(PERU));
    }

    /** Resultado de evaluar la capacidad; {@code licensedUntil} es la fecha declarada, si la hay. */
    public record CapacityView(ClinicalCapacityState state, String copNumber, LocalDate licensedUntil) {
        public boolean enabled() {
            return state == ClinicalCapacityState.ENABLED;
        }
    }

    /** Foto de la credencial que se copia al firmar (ADR-0016 D1, ADR-0018 D6). */
    public record SignerSnapshot(Long userId, String fullName, String copNumber, LocalDate licensedUntil) {}

    /** Regla pura, en el orden de {@link ClinicalCapacityState}: la primera condición que falla es el motivo. */
    static CapacityView evaluate(Membership membership, ProfessionalCredential credential, LocalDate today) {
        String cop = credential == null ? null : credential.getCopNumber();
        LocalDate until = credential == null ? null : credential.getLicensedUntil();
        ClinicalCapacityState state;
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            state = ClinicalCapacityState.INACTIVE_MEMBERSHIP;
        } else if (membership.getClinicalCapacityRevokedAt() != null) {
            state = ClinicalCapacityState.REVOKED;
        } else if (credential == null) {
            state = ClinicalCapacityState.NO_CREDENTIAL;
        } else if (until.isBefore(today)) {
            state = ClinicalCapacityState.EXPIRED;
        } else {
            state = ClinicalCapacityState.ENABLED;
        }
        return new CapacityView(state, cop, until);
    }

    /**
     * Capacidad del usuario de la sesión en la clínica activa, sin bloquear; vacía sin clínica activa
     * (design.md D3: la credencial es del usuario, la capacidad depende de una membresía).
     */
    @Transactional(readOnly = true)
    public Optional<CapacityView> current() {
        Long organizationId = OrganizationContext.get();
        if (organizationId == null) {
            return Optional.empty();
        }
        Long userId = IdentityContext.get();
        Membership membership = memberships.findByUserIdAndOrganizationId(userId, organizationId)
            .orElseThrow(() -> new AccessDeniedException("La sesión no pertenece a la clínica activa."));
        return Optional.of(evaluate(membership, credentials.findById(userId).orElse(null), todayInPeru()));
    }

    /** Capacidad de varios miembros de la clínica activa, para el listado del personal. */
    @Transactional(readOnly = true)
    public Map<Long, CapacityView> forMembers(Collection<Membership> members) {
        Map<Long, ProfessionalCredential> byUser = credentials
            .findAllById(members.stream().map(m -> m.getUser().getId()).toList()).stream()
            .collect(Collectors.toMap(ProfessionalCredential::getUserId, Function.identity()));
        LocalDate today = todayInPeru();
        return members.stream().collect(Collectors.toMap(
            m -> m.getUser().getId(),
            m -> evaluate(m, byUser.get(m.getUser().getId()), today)));
    }

    /**
     * Exige capacidad clínica para firmar y devuelve la foto que se guarda en el registro. Debe ser lo
     * primero de la transacción de la firma: bloquea membresía → credencial con {@code FOR SHARE}
     * (design.md D4), y después la firma bloquea sus filas clínicas.
     *
     * @throws ClinicalCapacityRequiredException si no tiene capacidad, con el motivo
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public SignerSnapshot require() {
        Long userId = IdentityContext.get();
        Long organizationId = OrganizationContext.get();
        if (userId == null || organizationId == null) {
            throw new AccessDeniedException("Firmar exige una sesión con clínica activa.");
        }
        Membership membership = memberships.lockForShare(userId, organizationId)
            .orElseThrow(() -> new AccessDeniedException("La sesión no pertenece a la clínica activa."));
        ProfessionalCredential credential = credentials.lockForShareByUserId(userId).orElse(null);
        CapacityView capacity = evaluate(membership, credential, todayInPeru());
        if (!capacity.enabled()) {
            throw new ClinicalCapacityRequiredException(capacity.state(), capacity.licensedUntil());
        }
        String fullName = users.findById(userId).orElseThrow().getFullName();
        return new SignerSnapshot(userId, fullName, credential.getCopNumber(), credential.getLicensedUntil());
    }
}
