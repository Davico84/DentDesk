package com.odontorisas.service.staff;

import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.InvitationStatus;
import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.Role;
import com.odontorisas.persistence.entity.Invitation;
import com.odontorisas.persistence.entity.Membership;
import com.odontorisas.persistence.entity.Organization;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.InvitationRepository;
import com.odontorisas.persistence.repository.MembershipRepository;
import com.odontorisas.persistence.repository.OrganizationRepository;
import com.odontorisas.persistence.repository.UserRepository;
import com.odontorisas.service.auth.EmailAlreadyExistsException;
import com.odontorisas.service.auth.InvalidInvitationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class StaffService {
    private static final Duration INVITATION_TTL = Duration.ofDays(7);
    private static final Duration VERIFICATION_TTL = Duration.ofMinutes(10);
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final InvitationRepository invitations;
    private final MembershipRepository memberships;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final Clock clock;
    private final InvitationMailer invitationMailer;
    private final ClinicalCapacityService clinicalCapacity;

    public StaffService(InvitationRepository invitations, MembershipRepository memberships,
                        OrganizationRepository organizations, UserRepository users, Clock clock,
                        InvitationMailer invitationMailer, ClinicalCapacityService clinicalCapacity) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.organizations = organizations;
        this.users = users;
        this.clock = clock;
        this.invitationMailer = invitationMailer;
        this.clinicalCapacity = clinicalCapacity;
    }

    public IssuedInvitation invite(String email, Role role) {
        if (IdentityContext.get() == null || OrganizationContext.get() == null || role != Role.MEMBER) {
            throw new AccessDeniedException("Solo se puede invitar personal de la clínica.");
        }
        Long organizationId = OrganizationContext.get();
        String normalizedEmail = email.trim().toLowerCase();
        users.findByEmail(normalizedEmail).ifPresent(user -> {
            if (memberships.findByUserIdAndOrganizationId(user.getId(), organizationId).isPresent()) {
                throw new MemberAlreadyExistsException(normalizedEmail);
            }
        });
        // Una vencida sigue en PENDING en la base (design D1): también ocupa el correo y se ofrece reenviarla.
        if (invitations.existsByOrganizationIdAndEmailIgnoreCaseAndStatus(organizationId, normalizedEmail, InvitationStatus.PENDING)) {
            throw new InvitationAlreadyPendingException();
        }
        var inviter = users.findById(IdentityContext.get()).orElseThrow();
        var organization = organizations.findById(organizationId).orElseThrow();
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String verificationCode = verificationCode();
        Instant verificationExpiresAt = clock.instant().plus(VERIFICATION_TTL);
        Invitation invitation = invitations.save(Invitation.builder()
            .organization(organization).invitedBy(inviter).email(normalizedEmail)
            .tokenHash(hash(token)).role(role).expiresAt(clock.instant().plus(INVITATION_TTL))
            .verificationCodeHash(hash(verificationCode)).verificationExpiresAt(verificationExpiresAt).build());
        IssuedInvitation issued = new IssuedInvitation(invitation.getId(), invitation.getEmail(), role, token, invitation.getExpiresAt());
        invitationMailer.send(new InvitationMailer.InvitationMail(
            invitation.getEmail(), organization.getName(), role, inviter.getFullName(), token, verificationCode,
            invitation.getExpiresAt()));
        return issued;
    }

    /**
     * Emite la invitacion inicial ADMIN para una organizacion recien aprovisionada. No comprueba
     * invitaciones pendientes: la organizacion acaba de crearse en esta misma transaccion.
     *
     * <p>El correo se envia DENTRO de la transaccion, despues de un flush (add-platform-admin D6):
     * si el SMTP falla, todo se revierte; si algo de la BD falla, falla antes de enviar. Solo un
     * fallo del propio COMMIT tras el envio dejaria un correo con un enlace que no resuelve.
     */
    public IssuedInvitation inviteInitialAdmin(String email, Organization organization, User inviter) {
        String normalizedEmail = email.trim().toLowerCase();
        if (users.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }
        String token = UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
        String verificationCode = verificationCode();
        Instant verificationExpiresAt = clock.instant().plus(VERIFICATION_TTL);
        Invitation invitation = invitations.saveAndFlush(Invitation.builder()
            .organization(organization).invitedBy(inviter).email(normalizedEmail)
            .tokenHash(hash(token)).role(Role.ADMIN)
            .expiresAt(clock.instant().plus(INVITATION_TTL))
            .verificationCodeHash(hash(verificationCode)).verificationExpiresAt(verificationExpiresAt).build());
        IssuedInvitation issued = new IssuedInvitation(invitation.getId(), invitation.getEmail(), Role.ADMIN,
            token, invitation.getExpiresAt());
        invitationMailer.send(new InvitationMailer.InvitationMail(
            invitation.getEmail(), organization.getName(), Role.ADMIN, inviter.getFullName(), token, verificationCode,
            invitation.getExpiresAt()));
        return issued;
    }

    @Transactional(readOnly = true)
    public InvitationView validate(String rawToken) {
        return InvitationView.from(pending(rawToken));
    }

    @Transactional(readOnly = true)
    public List<MemberView> members() {
        List<Membership> members = memberships.findByOrganizationId(OrganizationContext.get());
        // Una sola lectura de credenciales para todo el listado; la RLS solo deja ver las de miembros de la
        // clínica activa (design.md D1 de update-staff-professional-credential).
        var capacities = clinicalCapacity.forMembers(members);
        return members.stream()
            .map(m -> new MemberView(m.getUser().getId(), m.getUser().getEmail(), m.getUser().getFullName(), m.getRole(),
                m.getStatus(), capacities.get(m.getUser().getId())))
            .toList();
    }

    /** Invitaciones de la organización activa, más recientes primero, con "vencida" derivada (design D1). */
    @Transactional(readOnly = true)
    public List<InvitationListView> invitations() {
        Instant now = clock.instant();
        return invitations.findByOrganizationIdOrderByCreatedAtDesc(OrganizationContext.get()).stream()
            .map(i -> InvitationListView.of(i, now))
            .toList();
    }

    /**
     * Reenvía una invitación MEMBER pendiente o vencida: token, código y vigencias nuevos en la MISMA fila, de modo
     * que el enlace anterior deja de existir en el mismo commit (design D2). El correo sale tras el flush, dentro de
     * la transacción: si el SMTP falla, el rollback deja la invitación como estaba.
     */
    public InvitationListView resend(Long invitationId) {
        Invitation invitation = manageablePendingForUpdate(invitationId);
        String token = newToken();
        String verificationCode = verificationCode();
        Instant now = clock.instant();
        invitation.setTokenHash(hash(token));
        invitation.setExpiresAt(now.plus(INVITATION_TTL));
        invitation.setVerificationCodeHash(hash(verificationCode));
        invitation.setVerificationExpiresAt(now.plus(VERIFICATION_TTL));
        invitation.setVerificationAttempts(0);
        invitation.setVerifiedAt(null);
        invitations.saveAndFlush(invitation);
        invitationMailer.send(new InvitationMailer.InvitationMail(
            invitation.getEmail(), invitation.getOrganization().getName(), invitation.getRole(),
            invitation.getInvitedBy().getFullName(), token, verificationCode, invitation.getExpiresAt()));
        return InvitationListView.of(invitation, now);
    }

    public void revoke(Long invitationId) {
        manageablePendingForUpdate(invitationId).setStatus(InvitationStatus.REVOKED);
    }

    /**
     * Carga con bloqueo (primera lectura de la invitación en la transacción) y comprueba DESPUÉS de bloquear
     * (design D7): de otra clínica o inexistente → 404; ADMIN (la emite la plataforma) → 403; aceptada o revocada
     * → 409. Una vencida sigue en PENDING y sí se gestiona.
     */
    private Invitation manageablePendingForUpdate(Long invitationId) {
        Invitation invitation = invitations.findByIdForUpdate(invitationId)
            .filter(i -> i.getOrganization().getId().equals(OrganizationContext.get()))
            .orElseThrow(InvitationNotFoundException::new);
        if (invitation.getRole() != Role.MEMBER) {
            throw new AccessDeniedException("Las invitaciones de administrador las gestiona la plataforma.");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationNotPendingException();
        }
        return invitation;
    }

    public void changeStatus(Long memberId, MembershipStatus status) {
        if (memberId.equals(IdentityContext.get())) {
            throw new CannotChangeOwnStatusException();
        }
        var membership = memberships.findByUserIdAndOrganizationId(memberId, OrganizationContext.get())
            .orElseThrow(MemberNotFoundException::new);
        if (membership.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("No puedes desactivar al administrador de la organización.");
        }
        membership.setStatus(status);
    }

    /**
     * Retira o restituye la capacidad clínica de un {@code MEMBER} en la clínica activa
     * ({@code update-staff-professional-credential} design.md D2). Restituir solo levanta el retiro: la
     * capacidad sigue dependiendo de la credencial que declaró el profesional. La guarda de V30 repite
     * estas reglas en la base.
     */
    public void changeClinicalCapacity(Long memberId, boolean revoked) {
        Long adminId = IdentityContext.get();
        if (memberId.equals(adminId)) {
            throw new AccessDeniedException("No puedes retirar tu propia capacidad clínica.");
        }
        var membership = memberships.findByUserIdAndOrganizationId(memberId, OrganizationContext.get())
            .orElseThrow(MemberNotFoundException::new);
        if (membership.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("No puedes retirar la capacidad clínica de un administrador.");
        }
        if (revoked && membership.getClinicalCapacityRevokedAt() == null) {
            membership.setClinicalCapacityRevokedAt(clock.instant());
            membership.setClinicalCapacityRevokedBy(adminId);
        } else if (!revoked) {
            membership.setClinicalCapacityRevokedAt(null);
            membership.setClinicalCapacityRevokedBy(null);
        }
    }

    public Invitation consume(String rawToken, String email) {
        Invitation invitation = pending(rawToken);
        if (!invitation.getEmail().equalsIgnoreCase(email.trim())) throw new InvalidInvitationException();
        if (invitation.getVerifiedAt() == null) throw new InvalidInvitationException();
        // El hash en la condición: si un reenvío confirmó mientras esperábamos, el enlace usado ya no vale (D7).
        if (invitations.consume(invitation.getId(), hash(rawToken), InvitationStatus.ACCEPTED,
                clock.instant(), clock.instant()) != 1) {
            throw new InvalidInvitationException();
        }
        return invitation;
    }

    /**
     * Carga con bloqueo (design D7): sin él, una verificación que confirma después de un reenvío reescribe el hash
     * viejo, porque Hibernate escribe todas las columnas con lo que leyó, y deshace el reenvío.
     */
    @Transactional(noRollbackFor = InvalidInvitationException.class)
    public void verifyEmail(String rawToken, String email, String code) {
        Invitation invitation = pendingForUpdate(rawToken);
        if (!invitation.getEmail().equalsIgnoreCase(email.trim())
            || invitation.getVerificationCodeHash() == null
            || invitation.getVerificationExpiresAt() == null
            || !invitation.getVerificationExpiresAt().isAfter(clock.instant())
            || invitation.getVerifiedAt() != null
            || invitation.getVerificationAttempts() >= MAX_VERIFICATION_ATTEMPTS) {
            throw new InvalidInvitationException();
        }
        invitation.setVerificationAttempts(invitation.getVerificationAttempts() + 1);
        if (!invitation.getVerificationCodeHash().equals(hash(code.trim()))) {
            throw new InvalidInvitationException();
        }
        invitation.setVerifiedAt(clock.instant());
    }

    private Invitation pending(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new InvalidInvitationException();
        return requirePending(invitations.findByTokenHash(hash(rawToken)));
    }

    private Invitation pendingForUpdate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new InvalidInvitationException();
        return requirePending(invitations.findByTokenHashForUpdate(hash(rawToken)));
    }

    private Invitation requirePending(Optional<Invitation> found) {
        Invitation invitation = found.orElseThrow(InvalidInvitationException::new);
        if (invitation.getStatus() != InvitationStatus.PENDING || !invitation.getExpiresAt().isAfter(clock.instant())) {
            throw new InvalidInvitationException();
        }
        return invitation;
    }

    private static String newToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String verificationCode() {
        return Integer.toString(100000 + RANDOM.nextInt(900000));
    }

    public record IssuedInvitation(Long id, String email, Role role, String token, Instant expiresAt) {}
    public record InvitationView(String email, Role role, String organizationName, Instant expiresAt) {
        static InvitationView from(Invitation i) { return new InvitationView(i.getEmail(), i.getRole(), i.getOrganization().getName(), i.getExpiresAt()); }
    }
    public record MemberView(Long id, String email, String fullName, Role role, MembershipStatus status,
                             ClinicalCapacityService.CapacityView clinicalCapacity) {}

    /**
     * Invitación en el listado del administrador. Nunca lleva el token ni el código. {@code manageable}: si la
     * clínica puede reenviarla o revocarla (MEMBER pendiente o vencida, design D3); la regla vive solo aquí.
     */
    public record InvitationListView(Long id, String email, Role role, InvitationState state, Instant createdAt,
                                     Instant expiresAt, boolean manageable) {
        static InvitationListView of(Invitation i, Instant now) {
            InvitationState state = InvitationState.of(i.getStatus(), i.getExpiresAt(), now);
            boolean manageable = i.getRole() == Role.MEMBER
                && (state == InvitationState.PENDING || state == InvitationState.EXPIRED);
            return new InvitationListView(i.getId(), i.getEmail(), i.getRole(), state, i.getCreatedAt(),
                i.getExpiresAt(), manageable);
        }
    }
}
