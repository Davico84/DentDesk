package com.odontorisas.service.staff;

import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.InvitationStatus;
import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.Role;
import com.odontorisas.persistence.entity.Invitation;
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

    public StaffService(InvitationRepository invitations, MembershipRepository memberships,
                        OrganizationRepository organizations, UserRepository users, Clock clock,
                        InvitationMailer invitationMailer) {
        this.invitations = invitations;
        this.memberships = memberships;
        this.organizations = organizations;
        this.users = users;
        this.clock = clock;
        this.invitationMailer = invitationMailer;
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
        if (invitations.existsByOrganizationIdAndEmailIgnoreCaseAndStatus(organizationId, normalizedEmail, InvitationStatus.PENDING)) {
            throw new IllegalStateException("Ya existe una invitacion pendiente para ese correo.");
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
            invitation.getEmail(), organization.getName(), role, token, verificationCode, invitation.getExpiresAt()));
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
            invitation.getEmail(), organization.getName(), Role.ADMIN, token, verificationCode, invitation.getExpiresAt()));
        return issued;
    }

    @Transactional(readOnly = true)
    public InvitationView validate(String rawToken) {
        return InvitationView.from(pending(rawToken));
    }

    @Transactional(readOnly = true)
    public List<MemberView> members() {
        return memberships.findByOrganizationId(OrganizationContext.get()).stream()
            .map(m -> new MemberView(m.getUser().getId(), m.getUser().getEmail(), m.getUser().getFullName(), m.getRole(), m.getStatus()))
            .toList();
    }

    public void revoke(Long invitationId) {
        Invitation invitation = invitations.findById(invitationId).orElseThrow(InvalidInvitationException::new);
        if (!invitation.getOrganization().getId().equals(OrganizationContext.get())) {
            throw new AccessDeniedException("Invitacion fuera de la organizacion activa.");
        }
        invitation.setStatus(InvitationStatus.REVOKED);
    }

    public void changeStatus(Long memberId, MembershipStatus status) {
        if (memberId.equals(IdentityContext.get())) {
            throw new IllegalStateException("No puedes cambiar el estado de tu propia cuenta.");
        }
        var membership = memberships.findByUserIdAndOrganizationId(memberId, OrganizationContext.get())
            .orElseThrow(() -> new IllegalArgumentException("El miembro no pertenece a la organización activa."));
        if (membership.getRole() == Role.ADMIN) {
            throw new AccessDeniedException("No puedes desactivar al administrador de la organización.");
        }
        membership.setStatus(status);
    }

    public Invitation consume(String rawToken, String email) {
        Invitation invitation = pending(rawToken);
        if (!invitation.getEmail().equalsIgnoreCase(email.trim())) throw new InvalidInvitationException();
        if (invitation.getVerifiedAt() == null) throw new InvalidInvitationException();
        if (invitations.consume(invitation.getId(), InvitationStatus.ACCEPTED, clock.instant(), clock.instant()) != 1) {
            throw new InvalidInvitationException();
        }
        return invitation;
    }

    @Transactional(noRollbackFor = InvalidInvitationException.class)
    public void verifyEmail(String rawToken, String email, String code) {
        Invitation invitation = pending(rawToken);
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
        Invitation invitation = invitations.findByTokenHash(hash(rawToken)).orElseThrow(InvalidInvitationException::new);
        if (invitation.getStatus() != InvitationStatus.PENDING || !invitation.getExpiresAt().isAfter(clock.instant())) {
            throw new InvalidInvitationException();
        }
        return invitation;
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
    public record MemberView(Long id, String email, String fullName, Role role, MembershipStatus status) {}
}
