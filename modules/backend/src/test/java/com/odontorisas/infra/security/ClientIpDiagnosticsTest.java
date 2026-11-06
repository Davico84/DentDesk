package com.odontorisas.infra.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** Diagnóstico temporal de las cabeceras de IP: apagado no escribe nada y encendido nunca escribe una IP. */
@ExtendWith(OutputCaptureExtension.class)
class ClientIpDiagnosticsTest {

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Real-IP", "203.0.113.9");
        return request;
    }

    @Test
    void disabled_it_logs_nothing(CapturedOutput output) {
        new ClientIpDiagnostics(false).record(request(), "203.0.113.9");

        assertThat(output).doesNotContain("Diagnóstico IP");
    }

    @Test
    void enabled_it_logs_fingerprints_and_never_the_ip(CapturedOutput output) {
        new ClientIpDiagnostics(true).record(request(), "203.0.113.9");

        assertThat(output).contains("Diagnóstico IP").contains("CF-Connecting-IP=-")
            .doesNotContain("203.0.113.9").doesNotContain("10.0.0.7");
    }

    @Test
    void the_same_value_gives_the_same_fingerprint_within_one_boot(CapturedOutput output) {
        ClientIpDiagnostics diagnostics = new ClientIpDiagnostics(true);
        diagnostics.record(request(), "203.0.113.9");

        String line = output.getOut().lines().filter(l -> l.contains("Diagnóstico IP")).findFirst().orElseThrow();
        String realIp = line.replaceAll(".*X-Real-IP=(\\S+).*", "$1");
        String resolved = line.replaceAll(".*resolved=(\\S+).*", "$1");
        assertThat(realIp).isNotEqualTo("-").isEqualTo(resolved);
    }
}
