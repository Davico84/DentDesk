package com.odontorisas.staff;

import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.service.staff.InvitationMailer;
import com.odontorisas.service.staff.InvitationMailer.InvitationMail;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios de update-registration-organization-linking (tareas 6.1 y 6.2): autorización de la
 * emisión, invitaciones inválidas con error uniforme, replay, concurrencia y aislamiento por
 * organización. El correo es un mock del que se leen el token y el código. Se salta sin Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffInvitationIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean InvitationMailer mailer;

    @BeforeEach
    void resetMailer() {
        reset(mailer);
    }

    // ---------------------------------------------------------------- emisión (autorización)

    @Test
    void clinic_admin_cannot_grant_admin() throws Exception {
        Session admin = registerAdmin();
        String email = unique("quiere-admin");

        mockMvc.perform(invite(admin, email, "ADMIN"))
            .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM invitations WHERE email = ?", email)).isZero();
    }

    @Test
    void member_cannot_invite() throws Exception {
        Session admin = registerAdmin();
        Session member = registerMember(admin);
        String email = unique("otro");

        mockMvc.perform(invite(member, email, "MEMBER"))
            .andExpect(status().isForbidden());

        assertThat(count("SELECT count(*) FROM invitations WHERE email = ?", email)).isZero();
    }

    @Test
    void visitor_cannot_invite() throws Exception {
        mockMvc.perform(post("/staff/invitations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + unique("x") + "\",\"role\":\"MEMBER\"}"))
            .andExpect(status().isUnauthorized());
    }

    /** La respuesta de emisión no lleva el token: viaja solo en el correo (D1). */
    @Test
    void issued_member_invitation_response_does_not_expose_the_token() throws Exception {
        Session admin = registerAdmin();

        String body = mockMvc.perform(invite(admin, unique("m"), "MEMBER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(sentMail().rawToken());
    }

    // ---------------------------------------------------------------- Scenario: Invitación inválida

    /**
     * Inexistente, expirada, revocada, de otro correo o sin verificar: todas responden el MISMO
     * error, para no revelar cuál de las condiciones falló, y ninguna crea identidad.
     */
    @Test
    void every_invalid_invitation_gets_the_same_error_and_creates_nothing() throws Exception {
        Session admin = registerAdmin();

        String expiredEmail = unique("expirada");
        String expired = seedVerifiedInvitation("Clínica Expirada", expiredEmail, "MEMBER");
        jdbc.update("UPDATE invitations SET expires_at = now() - interval '1 minute' WHERE email = ?", expiredEmail);

        String unverifiedEmail = unique("sin-verificar");
        String unverified = seedVerifiedInvitation("Clínica Sin Verificar", unverifiedEmail, "MEMBER");
        jdbc.update("UPDATE invitations SET verified_at = NULL WHERE email = ?", unverifiedEmail);

        String revokedEmail = unique("revocada");
        long revokedId = issueMemberInvitationId(admin, revokedEmail);
        String revoked = sentMail().rawToken();
        mockMvc.perform(delete("/staff/invitations/" + revokedId).header("Authorization", admin.bearer()))
            .andExpect(status().isNoContent());
        jdbc.update("UPDATE invitations SET verified_at = now() WHERE id = ?", revokedId);

        String ownerEmail = unique("duena");
        String otherEmailToken = seedVerifiedInvitation("Clínica Otro Correo", ownerEmail, "MEMBER");

        record Case(String token, String email) {}
        List<Case> cases = List.of(
            new Case("no-existe-" + UUID.randomUUID(), unique("nadie")),
            new Case(expired, expiredEmail),
            new Case(unverified, unverifiedEmail),
            new Case(revoked, revokedEmail),
            new Case(otherEmailToken, unique("intruso")));

        for (Case c : cases) {
            mockMvc.perform(register(c.token(), c.email()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-invitation")));
            assertThat(count("SELECT count(*) FROM users WHERE email = ?", c.email()))
                .as("no crea identidad para %s", c.email()).isZero();
        }
    }

    @Test
    void register_without_token_is_rejected_and_creates_nothing() throws Exception {
        String email = unique("sin-token");

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Ana\"}"))
            .andExpect(status().isBadRequest());

        assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isZero();
    }

    /** Replay: una invitación ya consumida no vuelve a crear nada, ni con el mismo correo ni con otro. */
    @Test
    void a_consumed_invitation_cannot_be_replayed() throws Exception {
        String email = unique("replay");
        String token = seedVerifiedInvitation("Clínica Replay", email, "MEMBER");
        mockMvc.perform(register(token, email)).andExpect(status().isCreated());

        mockMvc.perform(register(token, email)).andExpect(status().is4xxClientError());
        String other = unique("replay-otro");
        mockMvc.perform(register(token, other))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value(endsWith("/invalid-invitation")));

        assertThat(count("SELECT count(*) FROM users WHERE email IN (?, ?)", email, other)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- verificación del correo (D2)

    @Test
    void a_wrong_code_is_rejected_and_attempts_are_capped() throws Exception {
        Session admin = registerAdmin();
        String email = unique("codigo");
        issueMemberInvitationId(admin, email);
        InvitationMail mail = sentMail();
        String wrong = mail.verificationCode().equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(verifyCode(mail.rawToken(), email, wrong)).andExpect(status().isBadRequest());
        }
        mockMvc.perform(verifyCode(mail.rawToken(), email, mail.verificationCode()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(register(mail.rawToken(), email))
            .andExpect(status().isBadRequest());
    }

    @Test
    void an_expired_code_is_rejected() throws Exception {
        Session admin = registerAdmin();
        String email = unique("codigo-vencido");
        long id = issueMemberInvitationId(admin, email);
        InvitationMail mail = sentMail();
        jdbc.update("UPDATE invitations SET verification_expires_at = now() - interval '1 second' WHERE id = ?", id);

        mockMvc.perform(verifyCode(mail.rawToken(), email, mail.verificationCode()))
            .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Scenario: Registro concurrente

    @Test
    void two_concurrent_registrations_with_the_same_invitation_create_exactly_one_identity() throws Exception {
        String email = unique("carrera");
        String token = seedVerifiedInvitation("Clínica Carrera", email, "MEMBER");
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            start.await();
            return mockMvc.perform(register(token, email)).andReturn().getResponse().getStatus();
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            results.add(pool.submit(attempt));
            results.add(pool.submit(attempt));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> r : results) {
                statuses.add(r.get());
            }

            assertThat(statuses).containsOnlyOnce(201);
            assertThat(statuses.stream().filter(s -> s != 201)).allMatch(s -> s >= 400 && s < 500);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", email)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM memberships m JOIN users u ON u.id = m.user_id WHERE u.email = ?",
            email)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM invitations WHERE email = ? AND status = 'ACCEPTED'", email))
            .isEqualTo(1);
    }

    // ---------------------------------------------------------------- aislamiento por organización (oid)

    @Test
    void member_joins_the_inviting_organization_with_the_oid_of_the_invitation() throws Exception {
        Session admin = registerAdmin();

        Session member = registerMember(admin);

        assertThat(member.organizationId()).isEqualTo(admin.organizationId());
        assertThat(member.role()).isEqualTo("MEMBER");
    }

    @Test
    void an_admin_only_sees_and_manages_its_own_organization() throws Exception {
        Session adminA = registerAdmin();
        Session memberA = registerMember(adminA);
        Session adminB = registerAdmin();
        long pendingInA = issueMemberInvitationId(adminA, unique("pendiente-a"));

        String membersOfB = mockMvc.perform(get("/staff/members").header("Authorization", adminB.bearer()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(membersOfB).doesNotContain(memberA.email()).doesNotContain(adminA.email());

        String membersOfA = mockMvc.perform(get("/staff/members").header("Authorization", adminA.bearer()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(membersOfA).contains(memberA.email()).contains(adminA.email());

        // add-staff design D3: de otra clínica responde como inexistente, sin revelar que existe.
        mockMvc.perform(delete("/staff/invitations/" + pendingInA).header("Authorization", adminB.bearer()))
            .andExpect(status().isNotFound());
        assertThat(count("SELECT count(*) FROM invitations WHERE id = ? AND status = 'PENDING'", pendingInA))
            .isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private record Session(String email, String accessToken, long organizationId, String role) {
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

    /** Invita por la API, toma token y código del correo, verifica y registra: el camino real. */
    private Session registerMember(Session admin) throws Exception {
        String email = unique("member");
        reset(mailer);
        issueMemberInvitationId(admin, email);
        InvitationMail mail = sentMail();
        mockMvc.perform(verifyCode(mail.rawToken(), email, mail.verificationCode()))
            .andExpect(status().isNoContent());
        MvcResult result = mockMvc.perform(register(mail.rawToken(), email))
            .andExpect(status().isCreated()).andReturn();
        reset(mailer);
        return session(email, result);
    }

    private long issueMemberInvitationId(Session admin, String email) throws Exception {
        String body = mockMvc.perform(invite(admin, email, "MEMBER"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static Session session(String email, MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return new Session(email, JsonPath.read(body, "$.accessToken"),
            ((Number) JsonPath.read(body, "$.user.organizationId")).longValue(),
            JsonPath.read(body, "$.user.role"));
    }

    private InvitationMail sentMail() {
        ArgumentCaptor<InvitationMail> captor = ArgumentCaptor.forClass(InvitationMail.class);
        verify(mailer, times(1)).send(captor.capture());
        return captor.getValue();
    }

    private static org.springframework.test.web.servlet.RequestBuilder invite(Session who, String email, String role) {
        return post("/staff/invitations").header("Authorization", who.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"}");
    }

    private static org.springframework.test.web.servlet.RequestBuilder verifyCode(String token, String email, String code) {
        return post("/auth/invitations/verify").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
    }

    private static org.springframework.test.web.servlet.RequestBuilder register(String token, String email) {
        return post("/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD
                + "\",\"fullName\":\"Persona Invitada\"}");
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@staff.test";
    }
}
