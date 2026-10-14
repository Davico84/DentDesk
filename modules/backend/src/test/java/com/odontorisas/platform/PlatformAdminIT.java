package com.odontorisas.platform;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jayway.jsonpath.JsonPath;
import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.common.Role;
import com.odontorisas.infra.security.PlatformBootstrapProperties;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.persistence.repository.OrganizationSettingsRepository;
import com.odontorisas.persistence.repository.PlatformAdminRepository;
import com.odontorisas.persistence.repository.UserRepository;
import com.odontorisas.service.platform.PlatformAdminBootstrapRunner;
import com.odontorisas.service.staff.InvitationDeliveryException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenarios de add-platform-admin (delta de multi-tenancy) a nivel HTTP contra PostgreSQL real.
 * El correo se sustituye por un mock: el test lee de él el token y el código que el destinatario
 * recibiría, igual que haría una persona con el correo real. Se salta sin Docker; corre en CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlatformAdminIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "plataforma-123";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenService tokenService;
    @Autowired UserRepository users;
    @Autowired PlatformAdminRepository platformAdmins;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean InvitationMailer mailer;
    @MockitoSpyBean OrganizationSettingsRepository settings;

    private String platformEmail;

    /** Siembra por el mismo bootstrap operativo que usa producción (tarea 1.3). */
    @BeforeEach
    void bootstrapPlatformAdmin() {
        reset(mailer, settings);
        platformEmail = unique("root");
        var runner = new PlatformAdminBootstrapRunner(
            new PlatformBootstrapProperties(true, platformEmail, "Plataforma", PASSWORD),
            users, platformAdmins, passwordEncoder);
        // Misma frontera transaccional que el @Transactional del bean en el arranque real.
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> runner.run(null));
    }

    /** Re-arrancar con el bootstrap activo no duplica la autorización ni rompe el login. */
    @Test
    void bootstrap_is_idempotent_against_the_real_database() throws Exception {
        var runner = new PlatformAdminBootstrapRunner(
            new PlatformBootstrapProperties(true, platformEmail, "Plataforma", "otra-clave-999"),
            users, platformAdmins, passwordEncoder);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> runner.run(null));

        assertThat(count("SELECT count(*) FROM platform_admins p JOIN users u ON u.id = p.user_id "
            + "WHERE u.email = ?", platformEmail)).isEqualTo(1);
        platformToken(); // la contraseña original sigue valiendo: el bootstrap no la pisa
    }

    // ---------------------------------------------------------------- sesión de plataforma

    @Test
    void platform_login_issues_a_token_without_oid() throws Exception {
        DecodedJWT jwt = JWT.decode(platformToken());

        assertThat(jwt.getClaim("oid").isMissing()).isTrue();
        assertThat(jwt.getClaim("authorities").asList(String.class)).containsExactly("ROLE_PLATFORM_ADMIN");
    }

    @Test
    void clinic_identity_cannot_log_in_as_platform() throws Exception {
        AdminSession admin = provisionAndRegisterAdmin("Clínica Login");

        mockMvc.perform(post("/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(admin.email(), PASSWORD)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void platform_login_locks_after_repeated_failures() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(credentials(platformEmail, "incorrecta")))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(platformEmail, PASSWORD)))
            .andExpect(status().isUnauthorized());
    }

    /** Scenario "PLATFORM_ADMIN opera sin tenant": sin oid no hay contexto clínico que usar. */
    @Test
    void platform_token_is_not_a_session_on_clinic_endpoints() throws Exception {
        String token = platformToken();

        mockMvc.perform(get("/staff/members").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/patients").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- Scenario: Plataforma crea una organización

    @Test
    void platform_admin_provisions_organization_settings_and_admin_invitation() throws Exception {
        String adminEmail = unique("admin");

        String body = mockMvc.perform(provision("Clínica Aurora", adminEmail, platformToken()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.organizationId").isNumber())
            .andExpect(jsonPath("$.name").value("Clínica Aurora"))
            .andExpect(jsonPath("$.adminEmail").value(adminEmail))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long organizationId = ((Number) JsonPath.read(body, "$.organizationId")).longValue();

        assertThat(count("SELECT count(*) FROM organization_settings WHERE organization_id = ?", organizationId))
            .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM invitations WHERE organization_id = ? AND email = ? "
            + "AND role = 'ADMIN' AND status = 'PENDING'", organizationId, adminEmail)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", adminEmail))
            .as("no crea directamente la cuenta del administrador").isZero();

        // Tarea 2.4: el token viaja una sola vez, por el mailer, y nunca en la respuesta.
        InvitationMail mail = sentMail();
        assertThat(mail.recipient()).isEqualTo(adminEmail);
        assertThat(mail.role()).isEqualTo(Role.ADMIN);
        assertThat(mail.organizationName()).isEqualTo("Clínica Aurora");
        assertThat(body).doesNotContain(mail.rawToken());
        assertThat(count("SELECT count(*) FROM invitations WHERE token_hash = ?", mail.rawToken()))
            .as("en BD solo se guarda el hash").isZero();
    }

    // ---------------------------------------------------------------- Scenario: Usuario no autorizado intenta aprovisionar

    @Test
    void visitor_cannot_provision() throws Exception {
        String name = "Clínica Visitante " + UUID.randomUUID();

        mockMvc.perform(post("/platform/organizations").contentType(MediaType.APPLICATION_JSON)
                .content(provisionBody(name, unique("x"))))
            .andExpect(status().isUnauthorized());

        assertNothingCreated(name);
    }

    @Test
    void clinic_admin_and_member_cannot_provision() throws Exception {
        String name = "Clínica Intrusa " + UUID.randomUUID();
        for (Role role : Role.values()) {
            String clinicToken = tokenService.issueAccessToken(1L, "clinica@test", 1L, role);
            mockMvc.perform(provision(name, unique("x"), clinicToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        assertNothingCreated(name);
    }

    // ---------------------------------------------------------------- Scenario: Aprovisionamiento atómico

    @Test
    void mail_failure_rolls_back_organization_settings_and_invitation() throws Exception {
        String name = "Clínica Sin Correo " + UUID.randomUUID();
        doThrow(new InvitationDeliveryException("SMTP caído")).when(mailer).send(any());

        mockMvc.perform(provision(name, unique("admin"), platformToken()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/invitation-delivery-failed")));

        assertNothingCreated(name);
    }

    @Test
    void settings_failure_rolls_back_and_sends_no_mail() throws Exception {
        String name = "Clínica Sin Settings " + UUID.randomUUID();
        doThrow(new IllegalStateException("fallo simulado")).when(settings).save(any());

        mockMvc.perform(provision(name, unique("admin"), platformToken()))
            .andExpect(status().isInternalServerError());

        assertNothingCreated(name);
        verify(mailer, never()).send(any());
    }

    /** La invitación falla (correo ya registrado) DESPUÉS de crear organización y settings. */
    @Test
    void invitation_failure_for_existing_identity_rolls_back_and_returns_409() throws Exception {
        String name = "Clínica Correo Tomado " + UUID.randomUUID();

        mockMvc.perform(provision(name, platformEmail, platformToken()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value(org.hamcrest.Matchers.endsWith("/email-already-exists")));

        assertNothingCreated(name);
        verify(mailer, never()).send(any());
    }

    // ---------------------------------------------------------------- Scenario: slug y validación

    @Test
    void same_name_twice_gets_a_disambiguated_slug() throws Exception {
        String name = "Clínica Gemela " + UUID.randomUUID();
        String token = platformToken();

        String first = mockMvc.perform(provision(name, unique("a"), token))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(provision(name, unique("b"), token))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String firstSlug = JsonPath.read(first, "$.slug");
        String secondSlug = JsonPath.read(second, "$.slug");
        assertThat(secondSlug).isNotEqualTo(firstSlug).startsWith(firstSlug);
    }

    @Test
    void missing_or_invalid_name_returns_400_and_creates_nothing() throws Exception {
        String token = platformToken();
        String tooLong = "C".repeat(121);

        for (String name : new String[] {"", " ", "A", tooLong}) {
            mockMvc.perform(provision(name, unique("admin"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'organizationName')]").isNotEmpty());
        }
        assertThat(count("SELECT count(*) FROM organizations WHERE name = ?", tooLong)).isZero();
        verify(mailer, never()).send(any());
    }

    // ---------------------------------------------------------------- Tarea 3.4: flujo completo

    /**
     * Orden de aplicación (proposal): plataforma crea la clínica → invitación ADMIN → registro del
     * ADMIN → invitación MEMBER → registro del MEMBER, ambos en la organización aprovisionada.
     */
    @Test
    void full_flow_platform_to_admin_to_member() throws Exception {
        AdminSession admin = provisionAndRegisterAdmin("Clínica Flujo Completo");

        String memberEmail = unique("member");
        clearInvocations(mailer);
        mockMvc.perform(post("/staff/invitations").header("Authorization", "Bearer " + admin.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + memberEmail + "\",\"role\":\"MEMBER\"}"))
            .andExpect(status().isOk());
        InvitationMail memberMail = sentMail();
        assertThat(memberMail.role()).isEqualTo(Role.MEMBER);

        String member = acceptInvitation(memberMail, "Mario Miembro");
        assertThat((String) JsonPath.read(member, "$.user.role")).isEqualTo("MEMBER");
        assertThat(((Number) JsonPath.read(member, "$.user.organizationId")).longValue())
            .isEqualTo(admin.organizationId());
        assertThat(count("SELECT count(*) FROM memberships m JOIN users u ON u.id = m.user_id "
            + "WHERE u.email = ? AND m.organization_id = ? AND m.role = 'MEMBER'",
            memberEmail, admin.organizationId())).isEqualTo(1);
    }

    @Test
    void admin_invitation_cannot_be_registered_twice() throws Exception {
        String adminEmail = unique("admin");
        mockMvc.perform(provision("Clínica Replay", adminEmail, platformToken()))
            .andExpect(status().isCreated());
        InvitationMail mail = sentMail();
        acceptInvitation(mail, "Ana Admin");

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(mail, "Otra Persona")))
            .andExpect(status().is4xxClientError());
        assertThat(count("SELECT count(*) FROM users WHERE email = ?", adminEmail)).isEqualTo(1);
    }

    // ---------------------------------------------------------------- helpers

    private record AdminSession(String email, String accessToken, long organizationId) {}

    private AdminSession provisionAndRegisterAdmin(String organizationName) throws Exception {
        String adminEmail = unique("admin");
        String provisioned = mockMvc.perform(provision(organizationName, adminEmail, platformToken()))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long organizationId = ((Number) JsonPath.read(provisioned, "$.organizationId")).longValue();

        String registered = acceptInvitation(sentMail(), "Ana Admin");

        assertThat((String) JsonPath.read(registered, "$.user.role")).isEqualTo("ADMIN");
        assertThat(((Number) JsonPath.read(registered, "$.user.organizationId")).longValue())
            .isEqualTo(organizationId);
        assertThat((String) JsonPath.read(registered, "$.user.organizationName")).isEqualTo(organizationName);
        assertThat(count("SELECT count(*) FROM memberships m JOIN users u ON u.id = m.user_id "
            + "WHERE u.email = ? AND m.organization_id = ? AND m.role = 'ADMIN'",
            adminEmail, organizationId)).isEqualTo(1);
        DecodedJWT jwt = JWT.decode(JsonPath.read(registered, "$.accessToken"));
        assertThat(jwt.getClaim("oid").asLong()).isEqualTo(organizationId);
        return new AdminSession(adminEmail, JsonPath.read(registered, "$.accessToken"), organizationId);
    }

    /** Lo que haría el destinatario con el correo: validar el enlace, verificar el código, registrarse. */
    private String acceptInvitation(InvitationMail mail, String fullName) throws Exception {
        mockMvc.perform(post("/auth/invitations/validate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + mail.rawToken() + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.role").value(mail.role().name()));
        mockMvc.perform(post("/auth/invitations/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + mail.rawToken() + "\",\"email\":\"" + mail.recipient()
                    + "\",\"code\":\"" + mail.verificationCode() + "\"}"))
            .andExpect(status().isNoContent());
        return mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(mail, fullName)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
    }

    private static String registerBody(InvitationMail mail, String fullName) {
        return "{\"token\":\"" + mail.rawToken() + "\",\"email\":\"" + mail.recipient()
            + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"" + fullName + "\"}";
    }

    private InvitationMail sentMail() {
        ArgumentCaptor<InvitationMail> captor = ArgumentCaptor.forClass(InvitationMail.class);
        verify(mailer, times(1)).send(captor.capture());
        return captor.getValue();
    }

    private String platformToken() throws Exception {
        String body = mockMvc.perform(post("/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(platformEmail, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static org.springframework.test.web.servlet.RequestBuilder provision(
            String name, String adminEmail, String token) {
        return post("/platform/organizations").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(provisionBody(name, adminEmail));
    }

    private static String provisionBody(String name, String adminEmail) {
        return "{\"organizationName\":\"" + name + "\",\"adminEmail\":\"" + adminEmail + "\"}";
    }

    private static String credentials(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private void assertNothingCreated(String organizationName) {
        assertThat(count("SELECT count(*) FROM organizations WHERE name = ?", organizationName)).isZero();
        assertThat(count("SELECT count(*) FROM organization_settings s JOIN organizations o "
            + "ON o.id = s.organization_id WHERE o.name = ?", organizationName)).isZero();
        assertThat(count("SELECT count(*) FROM invitations i JOIN organizations o "
            + "ON o.id = i.organization_id WHERE o.name = ?", organizationName)).isZero();
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@platform.test";
    }
}
