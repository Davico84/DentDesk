package com.odontorisas.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Diagnóstico temporal de las cabeceras de IP: apagado no escribe nada y encendido nunca escribe una IP. */
@ExtendWith(OutputCaptureExtension.class)
class ClientIpDiagnosticsTest {

    private static final List<String> IPS = List.of(
        "203.0.113.9", "198.51.100.4", "192.0.2.33", "2001:db8::1", "2001:db8:85a3::8a2e:370:7334", "10.0.0.7");

    /** Todas las cabeceras que el diagnóstico mira, con IPs reales de prueba (v4, v6 y listas). */
    private static MockHttpServletRequest fullRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.addHeader("CF-Connecting-IP", "198.51.100.4");
        request.addHeader("CF-Connecting-IPv6", "2001:db8::1");
        request.addHeader("True-Client-IP", "192.0.2.33");
        request.addHeader("X-Vercel-Forwarded-For", "203.0.113.9, 2001:db8:85a3::8a2e:370:7334");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 198.51.100.4");
        request.addHeader("Forwarded", "for=192.0.2.33");
        return request;
    }

    private static MockHttpServletRequest withRealIp(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Real-IP", ip);
        return request;
    }

    /** La petición tal como la ve el controller en producción: envuelta por el ForwardedHeaderFilter real. */
    private static HttpServletRequest throughForwardedHeaderFilter(MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        new ForwardedHeaderFilter().doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }

    private static List<String> lines(CapturedOutput output) {
        return output.getOut().lines().filter(l -> l.contains("Diagnóstico IP")).toList();
    }

    private static String field(String line, String name) {
        Matcher matcher = Pattern.compile(Pattern.quote(name) + "=(\\S+)").matcher(line);
        assertThat(matcher.find()).as(name).isTrue();
        return matcher.group(1);
    }

    @Test
    void disabled_it_logs_nothing(CapturedOutput output) throws Exception {
        new ClientIpDiagnostics(false).record(throughForwardedHeaderFilter(fullRequest()), "203.0.113.9");

        assertThat(lines(output)).isEmpty();
    }

    @Test
    void enabled_it_never_logs_an_ip_from_any_header(CapturedOutput output) throws Exception {
        new ClientIpDiagnostics(true).record(throughForwardedHeaderFilter(fullRequest()), "203.0.113.9");

        assertThat(lines(output)).hasSize(1);
        String line = lines(output).get(0);
        IPS.forEach(ip -> assertThat(line).doesNotContain(ip));
        assertThat(field(line, "Forwarded(raw)")).isEqualTo("presente");
    }

    @Test
    void the_same_ip_in_two_requests_gives_the_same_fingerprint_and_another_ip_a_different_one(CapturedOutput output) {
        ClientIpDiagnostics diagnostics = new ClientIpDiagnostics(true);
        diagnostics.record(withRealIp("203.0.113.9"), "203.0.113.9");
        diagnostics.record(withRealIp("203.0.113.9"), "203.0.113.9");
        diagnostics.record(withRealIp("198.51.100.4"), "198.51.100.4");

        List<String> logged = lines(output).stream().map(l -> field(l, "X-Real-IP")).toList();
        assertThat(logged.get(0)).isNotEqualTo("-").isEqualTo(logged.get(1));
        assertThat(logged.get(2)).isNotEqualTo(logged.get(0));
    }

    @Test
    void behind_the_forwarded_header_filter_it_still_sees_the_raw_x_forwarded_for_and_the_real_connection(
            CapturedOutput output) throws Exception {
        MockHttpServletRequest request = fullRequest();
        // Sin Forwarded: si viene, el filtro la prefiere a X-Forwarded-For para calcular remoteAddr.
        request.removeHeader("Forwarded");
        new ClientIpDiagnostics(true).record(throughForwardedHeaderFilter(request), "203.0.113.9");

        String line = lines(output).get(0);
        String[] xff = field(line, "X-Forwarded-For(raw)").split(",");
        String realIp = field(line, "X-Real-IP");
        String cfIp = field(line, "CF-Connecting-IP");
        // XFF crudo, una huella por elemento y en orden: 203.0.113.9 (= X-Real-IP), 198.51.100.4 (= CF-Connecting-IP).
        assertThat(xff).containsExactly(realIp, cfIp);
        // El filtro cambia remoteAddr por el primer valor de XFF; la conexión real (10.0.0.7) es otra.
        assertThat(field(line, "remoteAddr(filtro)")).isEqualTo(realIp);
        assertThat(field(line, "remoteAddr(conexión)")).isNotEqualTo(realIp).isNotEqualTo("-");
    }
}
