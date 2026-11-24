package com.odontorisas.service.staff;

import com.odontorisas.common.ClinicalCapacityState;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.SignerSnapshot;
import com.odontorisas.persistence.entity.Membership;
import com.odontorisas.persistence.entity.ProfessionalCredential;
import com.odontorisas.persistence.repository.MembershipRepository;
import com.odontorisas.persistence.repository.MembershipRepository.CapacityFacts;
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
import java.util.Comparator;
import java.util.List;
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
@Transactional(readOnly = true)
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

    /** Regla pura, en el orden de {@link ClinicalCapacityState}: la primera condición que falla es el motivo. */
    static CapacityView evaluate(Membership membership, ProfessionalCredential credential, LocalDate today) {
        return evaluate(membership.getStatus(), membership.getClinicalCapacityRevokedAt() != null,
            credential == null ? null : credential.getCopNumber(),
            credential == null ? null : credential.getLicensedUntil(), today);
    }

    /** La misma regla sobre los datos sueltos; {@code licensedUntil} {@code null} = sin credencial. */
    static CapacityView evaluate(MembershipStatus status, boolean revoked, String copNumber, LocalDate licensedUntil,
                                 LocalDate today) {
        ClinicalCapacityState state;
        if (status != MembershipStatus.ACTIVE) {
            state = ClinicalCapacityState.INACTIVE_MEMBERSHIP;
        } else if (revoked) {
            state = ClinicalCapacityState.REVOKED;
        } else if (licensedUntil == null) {
            state = ClinicalCapacityState.NO_CREDENTIAL;
        } else if (licensedUntil.isBefore(today)) {
            state = ClinicalCapacityState.EXPIRED;
        } else {
            state = ClinicalCapacityState.ENABLED;
        }
        return new CapacityView(state, copNumber, licensedUntil);
    }

    private static CapacityView evaluate(CapacityFacts facts, LocalDate date) {
        return evaluate(MembershipStatus.valueOf(facts.getMembershipStatus()), Boolean.TRUE.equals(facts.getCapacityRevoked()),
            null, facts.getLicensedUntil(), date);
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
     * Capacidad de otro miembro de la clínica activa en una fecha dada, sin bloquear (add-appointments design.md
     * D4: el odontólogo de una cita, en el día de la cita). Vacía si el usuario no es miembro de la clínica
     * activa. Lectura simple: retirar la capacidad después de agendar no cancela la cita, así que no hay carrera
     * que proteger.
     */
    @Transactional(readOnly = true)
    public Optional<CapacityView> onDate(Long userId, LocalDate date) {
        if (OrganizationContext.get() == null) {
            return Optional.empty();
        }
        return memberships.capacityFacts(userId).stream().findFirst().map(facts -> evaluate(facts, date));
    }

    /** Miembro con capacidad clínica vigente hoy, para elegirlo como odontólogo de una cita. */
    public record EnabledMember(Long userId, String fullName) {
    }

    /**
     * Los miembros de la clínica activa con capacidad clínica vigente hoy, por nombre (design.md D4). Lo ve
     * cualquier miembro: va por {@code clinic_capacity_facts}, no por la RLS de membresías y credenciales.
     */
    @Transactional(readOnly = true)
    public List<EnabledMember> enabledMembersToday() {
        if (OrganizationContext.get() == null) {
            return List.of();
        }
        LocalDate today = todayInPeru();
        return memberships.capacityFacts(null).stream()
            .filter(facts -> evaluate(facts, today).enabled())
            .map(facts -> new EnabledMember(facts.getUserId(), facts.getFullName()))
            .sorted(Comparator.comparing(EnabledMember::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
            .toList();
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
