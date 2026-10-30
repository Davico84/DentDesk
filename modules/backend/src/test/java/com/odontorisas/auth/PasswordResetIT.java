package com.odontorisas.auth;

import com.odontorisas.service.auth.AccountMailDeliveryException;
import com.odontorisas.service.auth.AccountMailer.PasswordChangedMail;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Restablecer la contraseña con el enlace por HTTP ({@code update-authentication-password-recovery} tarea 2.6).
 * Requirements de {@code authentication}: <i>Enlace de recuperación</i>, <i>Restablecer la contraseña con el
 * enlace</i>, <i>Cerrar todas las sesiones al restablecer la contraseña</i>, <i>Estado de la cuenta al
 * restablecer la contraseña</i>, <i>Recuperación excluida para administradores de plataforma</i> (enlace previo)
 * y <i>Aviso por correo del cambio de contraseña</i>.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetIT extends AbstractPasswordRecoveryIT {

    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String INVALID_LINK = "/errors/invalid-reset-link";

    private static void assertInvalidLink(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains(INVALID_LINK)
            .contains("El enlace no es válido o ya venció.").doesNotContain("outcome");
    }

    private static String outcome(MockHttpServletResponse response) throws Exception {
        return response.getContentAsString().replaceAll(".*\"outcome\":\"([A-Z_]+)\".*", "$1");
    }

    private Cookie aSession(String email) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andReturn().getResponse().getCookie(REFRESH_COOKIE);
    }

    private int refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/auth/refresh").cookie(cookie)).andReturn().getResponse().getStatus();
    }

    // ================================================================ Enlace de recuperación

    @Test
    void a_valid_link_resets_the_password_and_login_works_with_the_new_one_only() throws Exception {
        String email = newEmail("vigente");
        long user = activeMember(email);
        String token = link(user, Duration.ofMinutes(5));

        assertThat(check(token).getStatus()).isEqualTo(204);
        MockHttpServletResponse response = reset(token, NEW_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(outcome(response)).isEqualTo("OK");
        assertThat(login(email, NEW_PASSWORD)).isEqualTo(200);
        assertThat(login(email, PASSWORD)).isEqualTo(401);
    }

    @Test
    void a_link_older_than_30_minutes_is_rejected() throws Exception {
        long user = activeMember(newEmail("vencido"));
        String token = link(user, Duration.ofMinutes(31));

        assertInvalidLink(check(token));
        assertInvalidLink(reset(token, NEW_PASSWORD));
    }

    @Test
    void a_used_link_is_rejected_with_the_same_message() throws Exception {
        long user = activeMember(newEmail("usado"));
        String token = link(user, Duration.ofMinutes(1));
        assertThat(reset(token, NEW_PASSWORD).getStatus()).isEqualTo(200);

        assertInvalidLink(check(token));
        assertInvalidLink(reset(token, "otra-clave-9"));
    }

    @Test
    void a_link_revoked_by_a_new_one_is_rejected_and_the_new_one_still_works() throws Exception {
        String email = newEmail("anulado");
        long user = activeMember(email);
        String first = link(user, Duration.ofMinutes(2));
        assertThat(recover(email).getStatus()).isEqualTo(202);
        awaitIdle();
        String second = sentLinksTo(email).get(0).rawToken();

        assertInvalidLink(reset(first, NEW_PASSWORD));
        assertThat(check(second).getStatus()).isEqualTo(204);
    }

    @Test
    void an_unknown_link_reveals_nothing_about_the_account() throws Exception {
        assertInvalidLink(check("no-existe"));
        assertInvalidLink(reset("no-existe", NEW_PASSWORD));
    }

    // ================================================================ Restablecer la contraseña con el enlace

    @Test
    void a_short_new_password_is_rejected_without_consuming_the_link() throws Exception {
        long user = activeMember(newEmail("corta"));
        String token = link(user, Duration.ofMinutes(1));
        String hashBefore = userColumn(user, "password_hash");

        MockHttpServletResponse response = reset(token, "corta");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).doesNotContain(INVALID_LINK);
        assertThat(userColumn(user, "password_hash")).isEqualTo(hashBefore);
        assertThat(check(token).getStatus()).as("el enlace sigue vigente").isEqualTo(204);
    }

    // ================================================================ Cerrar todas las sesiones

    @Test
    void every_open_session_stops_renewing_and_no_new_session_is_issued() throws Exception {
        String email = newEmail("sesiones");
        long user = activeMember(email);
        Cookie phone = aSession(email);
        Cookie laptop = aSession(email);
        String token = link(user, Duration.ofMinutes(1));

        MockHttpServletResponse response = reset(token, NEW_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
        assertThat(refresh(phone)).isEqualTo(401);
        assertThat(refresh(laptop)).isEqualTo(401);
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE user_id = " + user)).isZero();
    }

    @Test
    void a_rejected_reset_closes_no_session() throws Exception {
        String email = newEmail("rechazo");
        long user = activeMember(email);
        Cookie session = aSession(email);
        String expired = link(user, Duration.ofMinutes(40));

        assertInvalidLink(reset(expired, NEW_PASSWORD));

        assertThat(refresh(session)).isEqualTo(200);
    }

    // ================================================================ Estado de la cuenta al restablecer

    @Test
    void a_locked_account_changes_its_password_but_stays_locked_until_the_planned_time() throws Exception {
        String email = newEmail("bloqueada");
        long user = activeMember(email);
        sql("UPDATE users SET failed_login_attempts = 5, locked_until = now() + interval '10 minutes' WHERE id = " + user);
        String lockedUntil = userColumn(user, "locked_until");
        String token = link(user, Duration.ofMinutes(1));

        MockHttpServletResponse response = reset(token, NEW_PASSWORD);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(outcome(response)).isEqualTo("LOCKED");
        assertThat(response.getContentAsString()).contains("\"lockedUntil\":\"");
        assertThat(userColumn(user, "locked_until")).isEqualTo(lockedUntil);
        assertThat(userColumn(user, "failed_login_attempts")).isEqualTo("5");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, userColumn(user, "password_hash"))).isTrue();
        assertThat(login(email, NEW_PASSWORD)).as("sigue bloqueada").isEqualTo(401);
    }

    @Test
    void an_identity_disabled_after_the_request_gets_access_disabled() throws Exception {
        String email = newEmail("desactivada");
        long user = activeMember(email);
        String token = link(user, Duration.ofMinutes(1));
        sql("UPDATE users SET status = 'DISABLED' WHERE id = " + user);

        MockHttpServletResponse response = reset(token, NEW_PASSWORD);

        assertThat(outcome(response)).isEqualTo("ACCESS_DISABLED");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, userColumn(user, "password_hash"))).isTrue();
        assertThat(userColumn(user, "status")).isEqualTo("DISABLED");
    }

    @Test
    void an_account_without_an_active_membership_gets_access_disabled() throws Exception {
        long user = user(newEmail("sin-acceso"), "ACTIVE");
        membership(user, organization("ACTIVE"), "INACTIVE");
        String token = link(user, Duration.ofMinutes(1));

        assertThat(outcome(reset(token, NEW_PASSWORD))).isEqualTo("ACCESS_DISABLED");
    }

    @Test
    void an_only_active_membership_in_a_suspended_clinic_gets_access_disabled() throws Exception {
        long user = user(newEmail("suspendida"), "ACTIVE");
        membership(user, organization("SUSPENDED"), "ACTIVE");
        String token = link(user, Duration.ofMinutes(1));

        assertThat(outcome(reset(token, NEW_PASSWORD))).isEqualTo("ACCESS_DISABLED");
    }

    @Test
    void without_a_current_lock_the_attempts_go_back_to_zero() throws Exception {
        long user = activeMember(newEmail("contador"));
        sql("UPDATE users SET failed_login_attempts = 5, locked_until = now() - interval '1 minute' WHERE id = " + user);
        String token = link(user, Duration.ofMinutes(1));

        assertThat(outcome(reset(token, NEW_PASSWORD))).isEqualTo("OK");

        assertThat(userColumn(user, "failed_login_attempts")).isEqualTo("0");
        assertThat(userColumn(user, "locked_until")).isNull();
    }

    // ================================================================ Plataforma

    @Test
    void a_link_issued_before_the_account_became_platform_admin_is_rejected() throws Exception {
        long user = activeMember(newEmail("promovida"));
        String token = link(user, Duration.ofMinutes(1));
        platformAdmin(user);

        assertInvalidLink(check(token));
        assertInvalidLink(reset(token, NEW_PASSWORD));
    }

    // ================================================================ Aviso por correo del cambio

    @Test
    void the_change_notice_is_sent_only_on_success() throws Exception {
        String email = newEmail("aviso");
        long user = activeMember(email);
        String expired = link(user, Duration.ofMinutes(40));
        assertInvalidLink(reset(expired, NEW_PASSWORD));
        awaitIdle();
        assertThat(sentNoticesTo(email)).as("un rechazo no avisa").isEmpty();

        assertThat(reset(link(user, Duration.ofMinutes(1)), NEW_PASSWORD).getStatus()).isEqualTo(200);
        awaitIdle();

        assertThat(sentNoticesTo(email)).hasSize(1);
    }

    @Test
    void a_failing_notice_does_not_undo_the_change() throws Exception {
        String email = newEmail("falla-aviso");
        long user = activeMember(email);
        doThrow(new AccountMailDeliveryException("caído", null)).when(mailer)
            .sendPasswordChanged(any(PasswordChangedMail.class));

        assertThat(reset(link(user, Duration.ofMinutes(1)), NEW_PASSWORD).getStatus()).isEqualTo(200);
        awaitIdle();

        assertThat(login(email, NEW_PASSWORD)).isEqualTo(200);
    }
}
