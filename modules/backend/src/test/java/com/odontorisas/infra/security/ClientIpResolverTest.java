package com.odontorisas.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IP del cliente para el límite de pedidos ({@code update-authentication-password-recovery} design.md D10).
 * Scenario: <i>Una IP declarada por el cliente no cuenta</i>.
 */
class ClientIpResolverTest {

    private static PasswordRecoveryProperties properties(String header) {
        return new PasswordRecoveryProperties(Duration.ofMinutes(30), 3, Duration.ofDays(1), header, 10,
            Duration.ofHours(1), 10_000, 100, 50);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        return request;
    }

    @Test
    void with_the_header_configured_it_reads_only_that_header() {
        MockHttpServletRequest request = request();
        request.addHeader("CF-Connecting-IP", "203.0.113.9");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.9");

        assertThat(new ClientIpResolver(properties("CF-Connecting-IP")).resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void an_x_forwarded_for_invented_by_the_client_does_not_change_the_counted_ip() {
        ClientIpResolver resolver = new ClientIpResolver(properties("CF-Connecting-IP"));
        MockHttpServletRequest first = request();
        first.addHeader("CF-Connecting-IP", "203.0.113.9");
        first.addHeader("X-Forwarded-For", "1.1.1.1");
        MockHttpServletRequest second = request();
        second.addHeader("CF-Connecting-IP", "203.0.113.9");
        second.addHeader("X-Forwarded-For", "2.2.2.2");

        assertThat(resolver.resolve(first)).isEqualTo(resolver.resolve(second));
    }

    @Test
    void with_the_header_configured_but_missing_all_requests_share_one_quota_and_never_the_remote_address() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "198.51.100.1");

        assertThat(new ClientIpResolver(properties("CF-Connecting-IP")).resolve(request))
            .isEqualTo(ClientIpResolver.UNKNOWN);
    }

    @Test
    void without_a_header_configured_it_uses_the_connection_address() {
        assertThat(new ClientIpResolver(properties("")).resolve(request())).isEqualTo("10.0.0.7");
    }
}
