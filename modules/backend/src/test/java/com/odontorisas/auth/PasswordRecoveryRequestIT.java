package com.odontorisas.auth;

import com.odontorisas.service.auth.AccountMailer.RecoveryMail;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El pedido de recuperación por HTTP ({@code update-authentication-password-recovery} tarea 2.5). Requirements
 * de {@code authentication}: <i>Pedir la recuperación de la contraseña</i>, <i>Límite de enlaces de
 * recuperación</i>, <i>Recuperación excluida para administradores de plataforma</i> y <i>Admisión de los pedidos
 * de recuperación</i> (el límite por IP; el pedido repetido vive en {@code PasswordRecoveryAntiEnumerationIT},
 * que puede detener el worker).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordRecoveryRequestIT extends AbstractPasswordRecoveryIT {

    private void assertAccepted(MockHttpServletResponse response) throws Exception {
        assertThat(response.getStatus()).isEqualTo(202);
        assertThat(response.getContentAsString()).isEmpty();
    }

    // ================================================================ Pedir la recuperación de la contraseña

    @Test
    void a_registered_email_gets_one_link_sent_only_to_that_email() throws Exception {
        String email = newEmail("activa");
        long user = activeMember(email);

        assertAccepted(recover(email));
        awaitIdle();

        List<RecoveryMail> sent = sentLinks();
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).recipient()).isEqualTo(email);
        assertThat(sent.get(0).expiresAt()).isBetween(Instant.now().plus(Duration.ofMinutes(29)),
            Instant.now().plus(Duration.ofMinutes(31)));
        // En la base queda solo el hash, nunca el token.
        assertThat(count("SELECT count(*) FROM password_reset_tokens WHERE user_id = " + user
            + " AND token_hash = '" + tokenService.hashRefreshToken(sent.get(0).rawToken()) + "'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM password_reset_tokens WHERE token_hash = '"
            + sent.get(0).rawToken() + "'")).isZero();
    }

    @Test
    void an_unregistered_email_gets_the_same_response_and_no_mail() throws Exception {
        assertAccepted(recover(newEmail("nadie")));
        awaitIdle();

        assertThat(sentLinks()).isEmpty();
    }

    @Test
    void an_email_with_uppercase_or_surrounding_spaces_is_the_same_account() throws Exception {
        String email = newEmail("mayus");
        activeMember(email);

        assertAccepted(recover("  " + email.toUpperCase() + " "));
        awaitIdle();

        assertThat(sentLinksTo(email)).hasSize(1);
    }

    @Test
    void an_empty_or_malformed_email_is_rejected_by_the_server() throws Exception {
        assertThat(recover("").getStatus()).isEqualTo(400);
        assertThat(recover("no-es-un-correo").getStatus()).isEqualTo(400);
        awaitIdle();

        assertThat(sentLinks()).isEmpty();
    }

    @Test
    void a_disabled_identity_gets_the_same_response_and_no_mail() throws Exception {
        String email = newEmail("desactivada");
        long user = user(email, "DISABLED");
        membership(user, organization("ACTIVE"), "ACTIVE");

        assertAccepted(recover(email));
        awaitIdle();

        assertThat(sentLinks()).isEmpty();
    }

    @Test
    void an_active_account_without_active_memberships_still_gets_the_link() throws Exception {
        String email = newEmail("sin-membresia");
        long user = user(email, "ACTIVE");
        membership(user, organization("ACTIVE"), "INACTIVE");

        assertAccepted(recover(email));
        awaitIdle();

        assertThat(sentLinksTo(email)).hasSize(1);
    }

    // ================================================================ Límite de enlaces de recuperación

    @Test
    void the_third_request_of_the_day_sends_and_the_fourth_does_not() throws Exception {
        String email = newEmail("limite");
        long user = activeMember(email);
        link(user, Duration.ofHours(3));
        link(user, Duration.ofHours(2));

        assertAccepted(recover(email));
        awaitIdle();
        assertThat(sentLinksTo(email)).as("tercero").hasSize(1);

        assertAccepted(recover(email));
        awaitIdle();
        assertThat(sentLinksTo(email)).as("cuarto: no se envía").hasSize(1);
    }

    @Test
    void after_24_hours_since_the_first_of_three_the_account_can_get_a_link_again() throws Exception {
        String email = newEmail("movil");
        long user = activeMember(email);
        link(user, Duration.ofHours(24).plusMinutes(1));
        link(user, Duration.ofHours(5));
        link(user, Duration.ofHours(4));

        assertAccepted(recover(email));
        awaitIdle();

        assertThat(sentLinksTo(email)).hasSize(1);
        // Al emitir, se borraron las filas de la cuenta de más de 24 h.
        assertThat(count("SELECT count(*) FROM password_reset_tokens WHERE user_id = " + user)).isEqualTo(3);
    }

    @Test
    void a_new_link_revokes_the_previous_valid_ones() throws Exception {
        String email = newEmail("anula");
        long user = activeMember(email);
        String previous = link(user, Duration.ofMinutes(5));

        assertAccepted(recover(email));
        awaitIdle();

        assertThat(count("SELECT count(*) FROM password_reset_tokens WHERE token_hash = '"
            + tokenService.hashRefreshToken(previous) + "' AND revoked_at IS NOT NULL")).isEqualTo(1);
    }

    // ================================================================ Recuperación excluida para plataforma

    @Test
    void a_platform_admin_gets_the_same_response_and_no_mail() throws Exception {
        String email = newEmail("plataforma");
        platformAdmin(user(email, "ACTIVE"));

        assertAccepted(recover(email));
        awaitIdle();

        assertThat(sentLinks()).isEmpty();
    }

    @Test
    void a_platform_admin_who_is_also_a_clinic_member_gets_no_mail() throws Exception {
        String email = newEmail("plataforma-miembro");
        platformAdmin(activeMember(email));

        assertAccepted(recover(email));
        awaitIdle();

        assertThat(sentLinks()).isEmpty();
    }

    // ================================================================ Admisión: límite por IP

    @Test
    void the_eleventh_request_from_the_same_ip_does_not_send() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertAccepted(recover(newEmail("relleno")));
        }
        String email = newEmail("undecimo");
        activeMember(email);

        assertAccepted(recover(email));
        awaitIdle();

        assertThat(sentLinksTo(email)).isEmpty();
    }
}
