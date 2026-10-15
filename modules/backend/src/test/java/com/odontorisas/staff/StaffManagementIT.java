package com.odontorisas.staff;

import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.service.staff.InvitationDeliveryException;
import com.odontorisas.service.staff.InvitationMailer;
import com.odontorisas.service.staff.InvitationMailer.InvitationMail;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios del spec {@code staff} (add-staff) contra PostgreSQL real. El correo es un mock del que se leen el
 * enlace y el código, como haría el invitado. Las carreras se fuerzan con un correo que se queda esperando
 * (el reenvío retiene el bloqueo de la fila) y {@code awaitLockWaitOrFinished}. Se salta sin Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffManagementIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokenService;
    @MockitoBean InvitationMailer mailer;

    @BeforeEach
    void resetMailer() {
        reset(mailer);
    }

    // ================================================================ Gestión exclusiva del administrador

    @Test
    void a_member_cannot_manage_staff() throws Exception {
        Session admin = registerAdmin();
        Session member = registerMember(admin);
        long invitation = issue(admin, unique("p"));

        for (RequestBuilder request : List.of(
                get("/staff/members").header("Authorization", member.bearer()),
                get("/staff/invitations").header("Authorization", member.bearer()),
                post("/staff/invitations/" + invitation + "/resend").header("Authorization", member.bearer()),
                delete("/staff/invitations/" + invitation).header("Authorization", member.bearer()))) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
        assertThat(state(invitation)).isEqualTo("PENDING");
    }

    @Test
    void a_visitor_and_a_platform_session_are_unauthenticated() throws Exception {
        String platform = "Bearer " + tokenService.issuePlatformAccessToken(1L, "root@platform.test");

        mockMvc.perform(get("/staff/invitations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/staff/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/staff/invitations").header("Authorization", platform)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/staff/members").header("Authorization", platform)).andExpect(status().isUnauthorized());
    }

    @Test
    void an_admin_never_reaches_another_clinic() throws Exception {
        Session adminA = registerAdmin();
        Session memberA = registerMember(adminA);
        long invitationA = issue(adminA, unique("a"));
        Session adminB = registerAdmin();

        String listB = body(get("/staff/invitations").header("Authorization", adminB.bearer()));
        assertThat(listB).doesNotContain(String.valueOf(invitationA)).doesNotContain(memberA.email());

        mockMvc.perform(post("/staff/invitations/" + invitationA + "/resend").header("Authorization", adminB.bearer()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value(endsWith("/invitation-not-found")));
        mockMvc.perform(delete("/staff/invitations/" + invitationA).header("Authorization", adminB.bearer()))
            .andExpect(status().isNotFound());
        mockMvc.perform(changeStatus(adminB, memberA.userId(), "INACTIVE"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value(endsWith("/member-not-found")));
        assertThat(state(invitationA)).isEqualTo("PENDING");
        assertThat(membershipStatus(memberA)).isEqualTo("ACTIVE");
    }

    // ================================================================ Invitar personal

    @Test
    void inviting_sends_the_mail_and_never_returns_the_token() throws Exception {
        Session admin = registerAdmin();

        String body = body(invite(admin, unique("nuevo")));

        assertThat(body).doesNotContain(sentMail().rawToken()).doesNotContain("token");
    }

    @Test
    void inviting_an_existing_member_or_a_pending_or_expired_email_is_a_conflict() throws Exception {
        Session admin = registerAdmin();
        Session member = registerMember(admin);
        mockMvc.perform(invite(admin, member.email()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value(endsWith("/member-already-exists")));

        String pendingEmail = unique("pendiente");
        issue(admin, pendingEmail);
        mockMvc.perform(invite(admin, pendingEmail))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value(endsWith("/invitation-already-pending")));

        String expiredEmail = unique("vencida");
        long expired = issue(admin, expiredEmail);
        expire(expired);
        mockMvc.perform(invite(admin, expiredEmail))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value(endsWith("/invitation-already-pending")))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("vencida")));

        assertThat(count("SELECT count(*) FROM invitations WHERE email IN (?, ?)", pendingEmail, expiredEmail))
            .isEqualTo(2);
    }

    @Test
    void a_mail_failure_creates_no_invitation() throws Exception {
        Session admin = registerAdmin();
        String email = unique("sin-correo");
        doThrow(new InvitationDeliveryException("SMTP caído")).when(mailer).send(any());

        mockMvc.perform(invite(admin, email)).andExpect(status().isServiceUnavailable());

        assertThat(count("SELECT count(*) FROM invitations WHERE email = ?", email)).isZero();
    }

    // ================================================================ Listado de invitaciones

    @Test
    void the_list_shows_every_state_newest_first_without_secrets() throws Exception {
        Session admin = registerAdmin();
        long accepted = invitationOf(registerMember(admin).email());
        long revoked = issue(admin, unique("revocada"));
        mockMvc.perform(delete("/staff/invitations/" + revoked).header("Authorization", admin.bearer()))
            .andExpect(status().isNoContent());
        long expired = issue(admin, unique("vencida"));
        expire(expired);
        long pending = issue(admin, unique("pendiente"));

        String list = body(get("/staff/invitations").header("Authorization", admin.bearer()));

        assertThat(stateIn(list, pending)).isEqualTo("PENDING");
        assertThat(stateIn(list, expired)).isEqualTo("EXPIRED");
        assertThat(stateIn(list, revoked)).isEqualTo("REVOKED");
        assertThat(stateIn(list, accepted)).isEqualTo("ACCEPTED");
        assertThat(manageableIn(list, pending)).isTrue();
        assertThat(manageableIn(list, expired)).isTrue();
        assertThat(manageableIn(list, revoked)).isFalse();
        assertThat(manageableIn(list, accepted)).isFalse();
        assertThat(manageableIn(list, invitationOf(admin.email()))).as("la ADMIN de la plataforma").isFalse();
        List<Integer> ids = JsonPath.read(list, "$[*].id");
        assertThat(ids.indexOf((int) pending)).isLessThan(ids.indexOf((int) revoked));
        assertThat(list).doesNotContain("token").doesNotContain("code").doesNotContain("Hash");
    }

    // ================================================================ Reenviar

    @Test
    void resending_after_the_code_expired_lets_the_invitee_register_and_voids_the_old_link() throws Exception {
        Session admin = registerAdmin();
        String email = unique("reenvio");
        long id = issue(admin, email);
        InvitationMail first = sentMail();
        jdbc.update("UPDATE invitations SET verification_expires_at = now() - interval '1 minute' WHERE id = ?", id);
        reset(mailer);

        mockMvc.perform(resend(admin, id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("PENDING"))
            .andExpect(jsonPath("$.token").doesNotExist());
        InvitationMail second = sentMail();

        assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
        mockMvc.perform(validate(first.rawToken())).andExpect(status().isBadRequest());
        mockMvc.perform(verifyCode(first.rawToken(), email, first.verificationCode())).andExpect(status().isBadRequest());
        mockMvc.perform(verifyCode(second.rawToken(), email, second.verificationCode())).andExpect(status().isNoContent());
        mockMvc.perform(register(second.rawToken(), email)).andExpect(status().isCreated());
    }

    @Test
    void resending_an_expired_invitation_makes_it_pending_again() throws Exception {
        Session admin = registerAdmin();
        long id = issue(admin, unique("vencida"));
        expire(id);

        mockMvc.perform(resend(admin, id)).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PENDING"));

        assertThat(count("SELECT count(*) FROM invitations WHERE id = ? AND expires_at > now() + interval '6 days'", id))
            .isEqualTo(1);
    }

    @Test
    void accepted_revoked_and_admin_invitations_cannot_be_resent_or_revoked() throws Exception {
        Session admin = registerAdmin();
        long accepted = invitationOf(registerMember(admin).email());
        long revoked = issue(admin, unique("revocada"));
        mockMvc.perform(delete("/staff/invitations/" + revoked).header("Authorization", admin.bearer()))
            .andExpect(status().isNoContent());
        long adminInvitation = seedPendingAdminInvitation(admin);
        reset(mailer);

        for (long id : new long[] {accepted, revoked}) {
            mockMvc.perform(resend(admin, id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/invitation-not-pending")));
            mockMvc.perform(delete("/staff/invitations/" + id).header("Authorization", admin.bearer()))
                .andExpect(status().isConflict());
        }
        mockMvc.perform(resend(admin, adminInvitation)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/staff/invitations/" + adminInvitation).header("Authorization", admin.bearer()))
            .andExpect(status().isForbidden());
        verify(mailer, never()).send(any());
        assertThat(state(accepted)).isEqualTo("ACCEPTED");
    }

    @Test
    void a_failed_resend_mail_keeps_the_invitation_as_it_was() throws Exception {
        Session admin = registerAdmin();
        long id = issue(admin, unique("fallo"));
        InvitationMail first = sentMail();
        String hashBefore = tokenHash(id);
        doThrow(new InvitationDeliveryException("SMTP caído")).when(mailer).send(any());

        mockMvc.perform(resend(admin, id)).andExpect(status().isServiceUnavailable());

        assertThat(tokenHash(id)).isEqualTo(hashBefore);
        mockMvc.perform(validate(first.rawToken())).andExpect(status().isOk());
    }

    // ================================================================ Revocar

    @Test
    void revoking_a_pending_invitation_voids_its_link() throws Exception {
        Session admin = registerAdmin();
        long id = issue(admin, unique("revocar"));
        InvitationMail mail = sentMail();

        mockMvc.perform(delete("/staff/invitations/" + id).header("Authorization", admin.bearer()))
            .andExpect(status().isNoContent());

        assertThat(state(id)).isEqualTo("REVOKED");
        mockMvc.perform(validate(mail.rawToken())).andExpect(status().isBadRequest());
    }

    // ================================================================ Desactivar y reactivar

    @Test
    void deactivating_blocks_login_and_refresh_keeps_authorship_and_reactivating_restores_access() throws Exception {
        Session admin = registerAdmin();
        Session member = registerMember(admin);
        long patient = createPatient(member);

        mockMvc.perform(changeStatus(admin, member.userId(), "INACTIVE")).andExpect(status().isNoContent());

        mockMvc.perform(login(member.email())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/refresh").cookie(member.refresh())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/patients/" + patient).header("Authorization", admin.bearer())).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM patients WHERE id = ? AND created_by = ?", patient, member.userId()))
            .isEqualTo(1);

        mockMvc.perform(changeStatus(admin, member.userId(), "ACTIVE")).andExpect(status().isNoContent());
        mockMvc.perform(login(member.email())).andExpect(status().isOk());
    }

    @Test
    void an_admin_cannot_change_its_own_status_nor_another_admins_nor_a_non_member() throws Exception {
        Session admin = registerAdmin();
        long otherAdmin = seedSecondAdmin(admin);
        Session outsider = registerAdmin();

        mockMvc.perform(changeStatus(admin, admin.userId(), "INACTIVE"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value(endsWith("/cannot-change-own-status")));
        mockMvc.perform(changeStatus(admin, otherAdmin, "INACTIVE")).andExpect(status().isForbidden());
        mockMvc.perform(changeStatus(admin, outsider.userId(), "INACTIVE")).andExpect(status().isNotFound());

        assertThat(membershipStatus(admin)).isEqualTo("ACTIVE");
        assertThat(count("SELECT count(*) FROM memberships WHERE user_id = ? AND status = 'ACTIVE'", otherAdmin))
            .isEqualTo(1);
    }

    // ================================================================ Concurrencia (design D7)

    /**
     * El registro con el enlace viejo espera el bloqueo que retiene el reenvío (su correo "tarda"); al confirmar el
     * reenvío, el hash cambió y el registro tiene que fallar. Sin el hash en la condición de consume(), el enlace
     * anulado registraba igual.
     */
    @Test
    void a_registration_with_the_old_link_loses_against_a_concurrent_resend() throws Exception {
        Session admin = registerAdmin();
        String email = unique("carrera");
        long id = issue(admin, email);
        InvitationMail first = sentMail();
        mockMvc.perform(verifyCode(first.rawToken(), email, first.verificationCode())).andExpect(status().isNoContent());
        CountDownLatch resendHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseMail = new CountDownLatch(1);
        reset(mailer);
        doAnswer(inv -> {
            resendHoldsLock.countDown();
            releaseMail.await(20, TimeUnit.SECONDS);
            return null;
        }).when(mailer).send(any());

        CompletableFuture<Integer> resend = CompletableFuture.supplyAsync(() -> statusOf(resend(admin, id)));
        assertThat(resendHoldsLock.await(20, TimeUnit.SECONDS)).isTrue();
        CountDownLatch registerDone = new CountDownLatch(1);
        CompletableFuture<Integer> registration = CompletableFuture.supplyAsync(() -> {
            try {
                return statusOf(register(first.rawToken(), email));
            } finally {
                registerDone.countDown();
            }
        });
        awaitLockWaitOrFinished(registerDone);
        releaseMail.countDown();

        assertThat(resend.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(registration.get(30, TimeUnit.SECONDS)).isEqualTo(400);
        assertThat(state(id)).isEqualTo("PENDING");
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isZero();
    }

    /**
     * La verificación del código viejo espera el bloqueo del reenvío. Sin bloquearla, leía la fila antes del
     * reenvío y, al confirmar después, reescribía el hash viejo: el reenvío quedaba deshecho.
     */
    @Test
    void a_verification_with_the_old_code_cannot_undo_a_concurrent_resend() throws Exception {
        Session admin = registerAdmin();
        String email = unique("verifica");
        long id = issue(admin, email);
        InvitationMail first = sentMail();
        CountDownLatch resendHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseMail = new CountDownLatch(1);
        ArgumentCaptor<InvitationMail> resent = ArgumentCaptor.forClass(InvitationMail.class);
        reset(mailer);
        doAnswer(inv -> {
            resendHoldsLock.countDown();
            releaseMail.await(20, TimeUnit.SECONDS);
            return null;
        }).when(mailer).send(resent.capture());

        CompletableFuture<Integer> resend = CompletableFuture.supplyAsync(() -> statusOf(resend(admin, id)));
        assertThat(resendHoldsLock.await(20, TimeUnit.SECONDS)).isTrue();
        CountDownLatch verifyDone = new CountDownLatch(1);
        CompletableFuture<Integer> verification = CompletableFuture.supplyAsync(() -> {
            try {
                return statusOf(verifyCode(first.rawToken(), email, first.verificationCode()));
            } finally {
                verifyDone.countDown();
            }
        });
        awaitLockWaitOrFinished(verifyDone);
        releaseMail.countDown();

        assertThat(resend.get(30, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(verification.get(30, TimeUnit.SECONDS)).isEqualTo(400);
        assertThat(tokenHash(id)).as("el reenvío no se deshace").isEqualTo(sha256(resent.getValue().rawToken()));
    }

    /** Si el registro ganó, el reenvío y la revocación llegan tarde: conflicto, y la aceptada sigue aceptada. */
    @Test
    void once_accepted_an_invitation_never_goes_back_to_pending() throws Exception {
        Session admin = registerAdmin();
        long accepted = invitationOf(registerMember(admin).email());

        mockMvc.perform(resend(admin, accepted)).andExpect(status().isConflict());
        mockMvc.perform(delete("/staff/invitations/" + accepted).header("Authorization", admin.bearer()))
            .andExpect(status().isConflict());

        assertThat(state(accepted)).isEqualTo("ACCEPTED");
    }

    /** Si la revocación ganó, el registro con ese enlace se rechaza. */
    @Test
    void once_revoked_the_link_cannot_register() throws Exception {
        Session admin = registerAdmin();
        String email = unique("revocada");
        long id = issue(admin, email);
        InvitationMail mail = sentMail();
        mockMvc.perform(verifyCode(mail.rawToken(), email, mail.verificationCode())).andExpect(status().isNoContent());
        mockMvc.perform(delete("/staff/invitations/" + id).header("Authorization", admin.bearer()))
            .andExpect(status().isNoContent());

        mockMvc.perform(register(mail.rawToken(), email)).andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isZero();
    }

    // ================================================================ helpers

    private record Session(String email, long userId, String accessToken, long organizationId, Cookie refresh) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    private Session registerAdmin() throws Exception {
        String email = unique("admin");
        MvcResult result = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(invitedAdminRegisterBody("Clínica " + email, email, PASSWORD, "Ana Admin")))
            .andExpect(status().isCreated()).andReturn();
        return session(email, result);
    }

    private Session registerMember(Session admin) throws Exception {
        String email = unique("member");
        reset(mailer);
        issue(admin, email);
        InvitationMail mail = sentMail();
        mockMvc.perform(verifyCode(mail.rawToken(), email, mail.verificationCode())).andExpect(status().isNoContent());
        MvcResult result = mockMvc.perform(register(mail.rawToken(), email)).andExpect(status().isCreated()).andReturn();
        reset(mailer);
        return session(email, result);
    }

    private static Session session(String email, MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Session(email, ((Number) JsonPath.read(body, "$.user.id")).longValue(),
            JsonPath.read(body, "$.accessToken"),
            ((Number) JsonPath.read(body, "$.user.organizationId")).longValue(),
            result.getResponse().getCookie("refresh_token"));
    }

    private long issue(Session admin, String email) throws Exception {
        return ((Number) JsonPath.read(body(invite(admin, email)), "$.id")).longValue();
    }

    private long createPatient(Session who) throws Exception {
        String body = body(post("/patients").header("Authorization", who.bearer()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"fullName\":\"Paciente " + UUID.randomUUID().toString().substring(0, 6)
                + "\",\"birthDate\":\"1990-01-01\",\"phone\":\"+51988877766\"}"), 201);
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Invitación ADMIN pendiente en la clínica, como la que emitiría la plataforma. */
    private long seedPendingAdminInvitation(Session admin) {
        return jdbc.queryForObject("INSERT INTO invitations(organization_id, invited_by, email, token_hash, role, expires_at) "
                + "VALUES (?, ?, ?, ?, 'ADMIN', now() + interval '7 days') RETURNING id", Long.class,
            admin.organizationId(), admin.userId(), unique("otro-admin"), sha256(UUID.randomUUID().toString()));
    }

    private long seedSecondAdmin(Session admin) {
        Long userId = jdbc.queryForObject("INSERT INTO users(email, password_hash, full_name, status) "
            + "VALUES (?, 'x', 'Otro Admin', 'ACTIVE') RETURNING id", Long.class, unique("admin2"));
        jdbc.update("INSERT INTO memberships(user_id, organization_id, role, status) VALUES (?, ?, 'ADMIN', 'ACTIVE')",
            userId, admin.organizationId());
        return userId;
    }

    private String body(RequestBuilder request) throws Exception {
        return body(request, 200);
    }

    private String body(RequestBuilder request, int expected) throws Exception {
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(expected);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int statusOf(RequestBuilder request) {
        try {
            return mockMvc.perform(request).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private InvitationMail sentMail() {
        ArgumentCaptor<InvitationMail> captor = ArgumentCaptor.forClass(InvitationMail.class);
        verify(mailer, times(1)).send(captor.capture());
        return captor.getValue();
    }

    private static RequestBuilder invite(Session admin, String email) {
        return post("/staff/invitations").header("Authorization", admin.bearer()).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"role\":\"MEMBER\"}");
    }

    private static RequestBuilder resend(Session admin, long id) {
        return post("/staff/invitations/" + id + "/resend").header("Authorization", admin.bearer());
    }

    private static RequestBuilder changeStatus(Session admin, long userId, String status) {
        return patch("/staff/members/" + userId + "/status").header("Authorization", admin.bearer())
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}");
    }

    private static RequestBuilder validate(String token) {
        return post("/auth/invitations/validate").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}");
    }

    private static RequestBuilder verifyCode(String token, String email, String code) {
        return post("/auth/invitations/verify").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
    }

    private static RequestBuilder register(String token, String email) {
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"fullName\":\"Persona Invitada\"}");
    }

    private static RequestBuilder login(String email) {
        return post("/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
    }

    private void expire(long invitationId) {
        jdbc.update("UPDATE invitations SET expires_at = now() - interval '1 minute' WHERE id = ?", invitationId);
    }

    private String state(long invitationId) {
        return jdbc.queryForObject("SELECT status FROM invitations WHERE id = ?", String.class, invitationId);
    }

    private String tokenHash(long invitationId) {
        return jdbc.queryForObject("SELECT token_hash FROM invitations WHERE id = ?", String.class, invitationId);
    }

    private long invitationOf(String email) {
        return jdbc.queryForObject("SELECT id FROM invitations WHERE email = ? ORDER BY id DESC LIMIT 1", Long.class, email);
    }

    private String membershipStatus(Session who) {
        return jdbc.queryForObject("SELECT status FROM memberships WHERE user_id = ? AND organization_id = ?",
            String.class, who.userId(), who.organizationId());
    }

    private static String stateIn(String list, long id) {
        List<String> states = JsonPath.read(list, "$[?(@.id == " + id + ")].state");
        assertThat(states).hasSize(1);
        return states.get(0);
    }

    private static boolean manageableIn(String list, long id) {
        List<Boolean> values = JsonPath.read(list, "$[?(@.id == " + id + ")].manageable");
        assertThat(values).hasSize(1);
        return values.get(0);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@staff.test";
    }
}
