package com.odontorisas.auth;

import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.common.Role;
import com.odontorisas.infra.concurrency.AccountTaskExecutors;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.service.auth.AccountMailer;
import com.odontorisas.service.auth.AccountMailer.PasswordChangedMail;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cambio de la contraseña propia por HTTP ({@code update-authentication-my-profile}). Scenarios de
 * {@code authentication}: <i>Cambiar la contraseña propia</i> y <i>Cerrar las otras sesiones al cambiar la
 * contraseña</i>. Las carreras viven en {@code ChangePasswordConcurrencyIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChangePasswordIT extends AbstractIntegrationTest {

    private static final String PATH = "/auth/password";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String CURRENT = "actual-123";
    private static final String NEW = "nueva-4567";
    private static final int MAX_ATTEMPTS = 5;

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AccountTaskExecutors executors;
    @MockitoBean AccountMailer mailer;

    private long clinicA;
    private long clinicB;
    private long user;
    private String email;

    @BeforeEach
    void aUserInTwoClinics() throws SQLException {
        String suffix = UUID.randomUUID().toString();
        email = "cp-" + suffix + "@test.local";
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            clinicA = id(s, "INSERT INTO organizations (name, slug) VALUES ('A', 'cp-a-" + suffix + "') RETURNING id");
            clinicB = id(s, "INSERT INTO organizations (name, slug) VALUES ('B', 'cp-b-" + suffix + "') RETURNING id");
            user = id(s, "INSERT INTO users (email, password_hash, full_name) VALUES ('" + email + "', '"
                + passwordEncoder.encode(CURRENT) + "', 'Dra. Cambio') RETURNING id");
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES ("
                + user + ", " + clinicA + ", 'ADMIN', 'ACTIVE')");
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES ("
                + user + ", " + clinicB + ", 'MEMBER', 'ACTIVE')");
        }
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String column(String column) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT " + column + " FROM users WHERE id = " + user)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private void sql(String statement) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute(statement);
        }
    }

    private String bearer(long org, Role role) {
        return "Bearer " + tokenService.issueAccessToken(user, email, org, role);
    }

    private String bearerA() {
        return bearer(clinicA, Role.ADMIN);
    }

    private static String body(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    private MvcResult change(String bearer, String current, String next) throws Exception {
        return mockMvc.perform(post(PATH).header("Authorization", bearer)
            .contentType(MediaType.APPLICATION_JSON).content(body(current, next))).andReturn();
    }

    private int login(String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andReturn().getResponse().getStatus();
    }

    /** Una sesión abierta (otro dispositivo): su cookie de refresco. */
    private Cookie aSession() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + CURRENT + "\"}"))
            .andExpect(status().isOk()).andReturn();
        return result.getResponse().getCookie(REFRESH_COOKIE);
    }

    private int refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/auth/refresh").cookie(cookie)).andReturn().getResponse().getStatus();
    }

    private Long organizationOf(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        String token = json.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        return tokenService.verifyAccessToken(token).organizationId();
    }

    // ================================================================ Cambiar la contraseña propia

    @Test
    void changing_the_password_makes_login_work_with_the_new_one_and_not_the_old() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", bearerA())
                .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, NEW)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value(email));

        assertThat(login(NEW)).isEqualTo(200);
        assertThat(login(CURRENT)).isEqualTo(401);
    }

    @Test
    void a_wrong_current_password_is_rejected_changes_nothing_and_counts_as_a_failure() throws Exception {
        String hashBefore = column("password_hash");

        mockMvc.perform(post(PATH).header("Authorization", bearerA())
                .contentType(MediaType.APPLICATION_JSON).content(body("no-es-esta", NEW)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type", endsWith("/errors/current-password-incorrect")))
            .andExpect(jsonPath("$.detail").value("La contraseña actual no es correcta."));

        assertThat(column("password_hash")).isEqualTo(hashBefore);
        assertThat(column("failed_login_attempts")).isEqualTo("1");
    }

    @Test
    void guessing_up_to_the_limit_locks_the_account_and_then_even_the_right_one_is_rejected() throws Exception {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            assertThat(change(bearerA(), "intento-" + i, NEW).getResponse().getStatus()).isEqualTo(400);
        }
        assertThat(column("locked_until")).isNotNull();

        mockMvc.perform(post(PATH).header("Authorization", bearerA())
                .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, NEW)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.type", endsWith("/errors/account-temporarily-locked")));
        // El login también queda bloqueado: es el mismo contador.
        assertThat(login(CURRENT)).isEqualTo(401);
        assertThat(passwordEncoder.matches(CURRENT, column("password_hash"))).isTrue();
    }

    @Test
    void a_successful_change_resets_the_attempts_and_leaves_no_lock() throws Exception {
        assertThat(change(bearerA(), "mal-1", NEW).getResponse().getStatus()).isEqualTo(400);
        assertThat(change(bearerA(), "mal-2", NEW).getResponse().getStatus()).isEqualTo(400);
        assertThat(column("failed_login_attempts")).isEqualTo("2");

        assertThat(change(bearerA(), CURRENT, NEW).getResponse().getStatus()).isEqualTo(200);

        assertThat(column("failed_login_attempts")).isEqualTo("0");
        assertThat(column("locked_until")).isNull();
    }

    @Test
    void a_successful_change_clears_an_already_expired_lock() throws Exception {
        sql("UPDATE users SET failed_login_attempts = 5, locked_until = now() - interval '1 minute' WHERE id = " + user);

        assertThat(change(bearerA(), CURRENT, NEW).getResponse().getStatus()).isEqualTo(200);

        assertThat(column("failed_login_attempts")).isEqualTo("0");
        assertThat(column("locked_until")).isNull();
    }

    @Test
    void a_short_new_password_is_rejected() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", bearerA())
                .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, "1234567")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[?(@.field == 'newPassword')]").isNotEmpty());
        assertThat(passwordEncoder.matches(CURRENT, column("password_hash"))).isTrue();
    }

    @Test
    void a_new_password_equal_to_the_current_is_rejected() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", bearerA())
                .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, CURRENT)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type", endsWith("/errors/new-password-same-as-current")));
        // No es un intento fallido: la actual era correcta.
        assertThat(column("failed_login_attempts")).isEqualTo("0");
    }

    @Test
    void a_platform_session_or_no_session_is_unauthenticated() throws Exception {
        String platform = "Bearer " + tokenService.issuePlatformAccessToken(1L, "root@platform.test");
        assertThat(change(platform, CURRENT, NEW).getResponse().getStatus()).isEqualTo(401);
        assertThat(mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, NEW)))
            .andReturn().getResponse().getStatus()).isEqualTo(401);
        assertThat(passwordEncoder.matches(CURRENT, column("password_hash"))).isTrue();
    }

    // ================================================================ Cerrar las otras sesiones

    @Test
    void another_open_session_can_no_longer_refresh() throws Exception {
        Cookie other = aSession();

        assertThat(change(bearerA(), CURRENT, NEW).getResponse().getStatus()).isEqualTo(200);

        assertThat(refresh(other)).isEqualTo(401);
    }

    @Test
    void the_current_session_stays_open_with_a_new_cookie_and_the_old_cookie_dies() throws Exception {
        Cookie current = aSession();

        MvcResult changed = mockMvc.perform(post(PATH).header("Authorization", bearerA()).cookie(current)
                .contentType(MediaType.APPLICATION_JSON).content(body(CURRENT, NEW)))
            .andExpect(status().isOk())
            .andExpect(header().string("Set-Cookie", allOf(
                containsString(REFRESH_COOKIE + "="), containsString("HttpOnly"), containsString("Secure"),
                containsString("SameSite=Lax"), containsString("Path=/api/auth"))))
            .andReturn();
        Cookie renewed = changed.getResponse().getCookie(REFRESH_COOKIE);

        // Recargar la aplicación = renovar con la cookie nueva.
        assertThat(refresh(renewed)).isEqualTo(200);
        // La cookie anterior de esta misma sesión también se cerró: no sirve si se filtró con la contraseña.
        assertThat(refresh(current)).isEqualTo(401);
    }

    @Test
    void the_current_session_stays_in_its_clinic_and_the_other_clinics_close() throws Exception {
        Cookie other = aSession();

        MvcResult fromB = change(bearer(clinicB, Role.MEMBER), CURRENT, NEW);
        assertThat(fromB.getResponse().getStatus()).isEqualTo(200);
        assertThat(organizationOf(fromB)).isEqualTo(clinicB);

        MvcResult fromA = change(bearerA(), NEW, CURRENT);
        assertThat(fromA.getResponse().getStatus()).isEqualTo(200);
        assertThat(organizationOf(fromA)).isEqualTo(clinicA);

        assertThat(refresh(other)).isEqualTo(401);
        assertThat(refresh(fromB.getResponse().getCookie(REFRESH_COOKIE))).isEqualTo(401);
    }

    @Test
    void a_session_whose_membership_is_no_longer_active_cannot_change_and_nothing_closes() throws Exception {
        Cookie other = aSession();
        sql("UPDATE memberships SET status = 'INACTIVE' WHERE user_id = " + user + " AND organization_id = " + clinicB);

        assertThat(change(bearer(clinicB, Role.MEMBER), CURRENT, NEW).getResponse().getStatus()).isEqualTo(401);

        assertThat(passwordEncoder.matches(CURRENT, column("password_hash"))).isTrue();
        assertThat(refresh(other)).isEqualTo(200);
    }

    @Test
    void a_rejected_change_closes_no_session() throws Exception {
        Cookie first = aSession();
        Cookie second = aSession();

        change(bearerA(), "no-es-esta", NEW);
        change(bearerA(), CURRENT, CURRENT);
        change(bearerA(), CURRENT, "corta");

        assertThat(refresh(first)).isEqualTo(200);
        assertThat(refresh(second)).isEqualTo(200);
    }

    // ================================================================ Aviso por correo del cambio de contraseña
    // (update-authentication-password-recovery tarea 2.4)

    private List<PasswordChangedMail> noticesSent() throws InterruptedException {
        assertThat(executors.awaitIdle(Duration.ofSeconds(20))).isTrue();
        ArgumentCaptor<PasswordChangedMail> captor = ArgumentCaptor.forClass(PasswordChangedMail.class);
        verify(mailer, atLeast(0)).sendPasswordChanged(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void a_successful_change_sends_the_notice_to_the_account_email() throws Exception {
        assertThat(change(bearerA(), CURRENT, NEW).getResponse().getStatus()).isEqualTo(200);

        assertThat(noticesSent()).extracting(PasswordChangedMail::recipient).containsExactly(email);
    }

    @Test
    void a_rejected_change_sends_no_notice() throws Exception {
        assertThat(change(bearerA(), "no-es-esta", NEW).getResponse().getStatus()).isEqualTo(400);
        assertThat(change(bearerA(), CURRENT, CURRENT).getResponse().getStatus()).isEqualTo(400);

        assertThat(noticesSent()).isEmpty();
    }

    /** Requirement <i>Admisión de los pedidos de recuperación</i>: <i>Los avisos no esperan a los pedidos</i>. */
    @Test
    void the_notice_is_sent_even_with_the_recovery_queue_saturated() throws Exception {
        CountDownLatch hold = new CountDownLatch(1);
        try {
            try {
                for (int i = 0; i < 1_000; i++) {
                    executors.submitRecovery(() -> {
                        try {
                            hold.await(30, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                }
            } catch (RejectedExecutionException full) {
                // la cola de recuperación quedó llena
            }

            assertThat(change(bearerA(), CURRENT, NEW).getResponse().getStatus()).isEqualTo(200);

            ArgumentCaptor<PasswordChangedMail> captor = ArgumentCaptor.forClass(PasswordChangedMail.class);
            verify(mailer, org.mockito.Mockito.timeout(10_000)).sendPasswordChanged(captor.capture());
            assertThat(captor.getValue().recipient()).isEqualTo(email);
        } finally {
            hold.countDown();
        }
    }
}
