package com.odontorisas.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * La IP del cliente con una cabecera configurada ({@code update-authentication-password-recovery} design.md D10,
 * tarea 2.5). Scenario <i>Una IP declarada por el cliente no cuenta</i>: un {@code X-Forwarded-For} distinto en cada
 * pedido no evita el límite, porque solo cuenta la cabecera configurada. La fuente de producción, sin cabecera,
 * la cubre {@code ClientIpResolverTest}.
 */
@SpringBootTest(properties = "app.password-recovery.client-ip-header=CF-Connecting-IP")
@AutoConfigureMockMvc
class PasswordRecoveryClientIpIT extends AbstractPasswordRecoveryIT {

    private MockHttpServletResponse recoverWithForwardedFor(String email, String forwardedFor) throws Exception {
        return mockMvc.perform(post("/auth/password-recovery").contentType(MediaType.APPLICATION_JSON)
            .header("CF-Connecting-IP", ip)
            .header("X-Forwarded-For", forwardedFor)
            .content("{\"email\":\"" + email + "\"}")).andReturn().getResponse();
    }

    @Test
    void a_different_x_forwarded_for_on_each_request_does_not_avoid_the_limit() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(recoverWithForwardedFor(newEmail("relleno"), "198.51.100." + i).getStatus()).isEqualTo(202);
        }
        String email = newEmail("undecimo");
        activeMember(email);

        assertThat(recoverWithForwardedFor(email, "198.51.100.99").getStatus()).isEqualTo(202);
        awaitIdle();

        assertThat(sentLinksTo(email)).isEmpty();
    }
}
