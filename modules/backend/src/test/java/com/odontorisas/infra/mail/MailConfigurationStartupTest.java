package com.odontorisas.infra.mail;

import com.odontorisas.service.auth.AccountMailer;
import com.odontorisas.service.auth.AccountMailer.RecoveryMail;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque con el binding real de {@code app.mail.*} y {@link MailConfiguration}
 * ({@code update-project-foundation-local-mail} spec "Modo de correo local acotado a la máquina"): el modo local
 * con un host remoto impide arrancar sin revelar credenciales, el modo local con correo deshabilitado arranca, el
 * {@code application.yml} lo deja apagado por defecto, y el {@link AccountMailer} que arma Spring entrega en modo
 * local sin STARTTLS ni AUTH.
 */
class MailConfigurationStartupTest {

    private static final String SECRET_USER = "usuario-marcador-QZX";
    private static final String SECRET_PASSWORD = "clave-marcador-QZX-9f8e7d";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(MailConfiguration.class);

    private static String[] mail(String enabled, String localSmtp, String host, int port) {
        return new String[] {
            "app.mail.enabled=" + enabled, "app.mail.local-smtp=" + localSmtp, "app.mail.host=" + host,
            "app.mail.port=" + port, "app.mail.username=" + SECRET_USER, "app.mail.password=" + SECRET_PASSWORD,
            "app.mail.from=no-reply@dentdesk.test", "app.mail.frontend-url=http://localhost:5173",
            "app.mail.timeout=PT2S"};
    }

    private static String failureChain(Throwable failure) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            sb.append(t).append('\n');
        }
        return sb.toString();
    }

    @Test
    void local_mode_with_a_remote_host_does_not_start_and_reveals_no_credentials() {
        runner.withPropertyValues(mail("true", "true", "smtp-relay.brevo.com", 587)).run(context -> {
            assertThat(context).hasFailed();
            Throwable failure = context.getStartupFailure();
            String chain = failureChain(failure);
            assertThat(chain).contains("MAIL_LOCAL_SMTP").contains("smtp-relay.brevo.com")
                .doesNotContain(SECRET_USER).doesNotContain(SECRET_PASSWORD);
            // El reporte de arranque de Spring muestra el valor de la propiedad que falló al enlazar: no puede ser
            // una credencial.
            for (Throwable t = failure; t != null; t = t.getCause()) {
                if (t instanceof BindException bind && bind.getProperty() != null) {
                    assertThat(bind.getProperty().getName().toString())
                        .isNotIn("app.mail.username", "app.mail.password");
                }
            }
        });
    }

    @Test
    void local_mode_with_mail_disabled_starts_without_smtp_mailers() {
        runner.withPropertyValues(mail("false", "true", "smtp-relay.brevo.com", 587)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SmtpAccountMailer.class);
            assertThat(context).hasSingleBean(AccountMailer.class);
        });
    }

    @Test
    void application_yml_leaves_local_mode_off_by_default() throws Exception {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
        runner
            .withInitializer(context -> yaml.forEach(source -> context.getEnvironment().getPropertySources().addLast(source)))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(MailProperties.class).localSmtp()).isFalse();
            });
    }

    @Test
    void the_configured_account_mailer_delivers_in_local_mode_without_starttls_or_auth() throws Exception {
        try (FakeSmtpServer server = new FakeSmtpServer(false)) {
            runner.withPropertyValues(mail("true", "true", "localhost", server.port())).run(context -> {
                assertThat(context).hasNotFailed();
                context.getBean(AccountMailer.class)
                    .sendRecoveryLink(new RecoveryMail("ana@clinica.test", "tok", Instant.parse("2026-11-20T00:00:00Z")));
            });

            assertThat(server.commands()).contains("DATA").doesNotContain("STARTTLS", "AUTH");
        }
    }
}
