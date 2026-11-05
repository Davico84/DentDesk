package com.odontorisas.auth;

import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.infra.concurrency.AccountTaskExecutors;
import com.odontorisas.infra.security.TokenService;
import com.odontorisas.service.auth.AccountMailer;
import com.odontorisas.service.auth.AccountMailer.PasswordChangedMail;
import com.odontorisas.service.auth.AccountMailer.RecoveryMail;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Base de los ITs de recuperación de la contraseña ({@code update-authentication-password-recovery}). El mailer
 * es un mock que registra lo enviado; los ITs esperan a que los executors terminen antes de afirmar.
 *
 * <p>Cada test pide desde una <b>IP propia</b>: el límite por IP vive en memoria y es del contexto de Spring,
 * que se comparte entre tests.
 */
abstract class AbstractPasswordRecoveryIT extends AbstractIntegrationTest {

    protected static final String PASSWORD = "actual-123";
    protected static final String NEW_PASSWORD = "nueva-4567";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected TokenService tokenService;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected AccountTaskExecutors executors;
    @MockitoBean protected AccountMailer mailer;

    protected String suffix;
    protected String ip;
    private int emails;

    @BeforeEach
    void uniqueSuffixAndIp() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        ip = "10." + random.nextInt(256) + "." + random.nextInt(256) + "." + random.nextInt(1, 255);
    }

    // ================================================================ datos

    protected String newEmail(String label) {
        return label + "-" + suffix + "-" + (++emails) + "@recovery.test";
    }

    protected long organization(String status) throws SQLException {
        return id("INSERT INTO organizations (name, slug, status) VALUES ('Clínica', 'pr-" + suffix + "-" + (++emails)
            + "', '" + status + "') RETURNING id");
    }

    protected long user(String email, String status) throws SQLException {
        return id("INSERT INTO users (email, password_hash, full_name, status) VALUES ('" + email + "', '"
            + passwordEncoder.encode(PASSWORD) + "', 'Dra. Recupera', '" + status + "') RETURNING id");
    }

    /** Usuario {@code ACTIVE} con una membresía {@code ACTIVE} en una clínica {@code ACTIVE}. */
    protected long activeMember(String email) throws SQLException {
        long user = user(email, "ACTIVE");
        membership(user, organization("ACTIVE"), "ACTIVE");
        return user;
    }

    protected void membership(long user, long organization, String status) throws SQLException {
        sql("INSERT INTO memberships (user_id, organization_id, role, status) VALUES (" + user + ", " + organization
            + ", 'ADMIN', '" + status + "')");
    }

    protected void platformAdmin(long user) throws SQLException {
        sql("INSERT INTO platform_admins (user_id) VALUES (" + user + ")");
    }

    /**
     * Un enlace emitido hace {@code age}, con su vigencia de 30 min desde la emisión. Devuelve el token en claro.
     */
    protected String link(long user, Duration age) throws SQLException {
        String raw = "raw-" + UUID.randomUUID();
        sql("INSERT INTO password_reset_tokens (user_id, token_hash, created_at, expires_at) VALUES (" + user + ", '"
            + tokenService.hashRefreshToken(raw) + "', now() - interval '" + age.toSeconds() + " seconds', "
            + "now() - interval '" + age.toSeconds() + " seconds' + interval '30 minutes')");
        return raw;
    }

    protected void sql(String statement) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute(statement);
        }
    }

    protected long id(String sql) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    protected String userColumn(long user, String column) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT " + column + " FROM users WHERE id = " + user)) {
            rs.next();
            return rs.getString(1);
        }
    }

    protected long count(String sql) throws SQLException {
        return id(sql);
    }

    // ================================================================ peticiones

    /** La petición desde la IP de este test. */
    protected MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    protected MockHttpServletResponse recover(String email) throws Exception {
        return mockMvc.perform(fromIp(post("/auth/password-recovery")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}")).andReturn().getResponse();
    }

    protected MockHttpServletResponse check(String token) throws Exception {
        return mockMvc.perform(post("/auth/password-reset/check").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}")).andReturn().getResponse();
    }

    protected MockHttpServletResponse reset(String token, String newPassword) throws Exception {
        return mockMvc.perform(post("/auth/password-reset").contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}")).andReturn().getResponse();
    }

    protected int login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andReturn().getResponse().getStatus();
    }

    // ================================================================ lo enviado

    protected void awaitIdle() throws InterruptedException {
        assertThat(executors.awaitIdle(Duration.ofSeconds(20))).as("los executors terminaron su cola").isTrue();
    }

    /** Enlaces enviados hasta ahora (después de {@link #awaitIdle}). */
    protected List<RecoveryMail> sentLinks() {
        ArgumentCaptor<RecoveryMail> captor = ArgumentCaptor.forClass(RecoveryMail.class);
        verify(mailer, atLeast(0)).sendRecoveryLink(captor.capture());
        return captor.getAllValues();
    }

    protected List<RecoveryMail> sentLinksTo(String email) {
        return sentLinks().stream().filter(mail -> mail.recipient().equalsIgnoreCase(email)).toList();
    }

    protected List<PasswordChangedMail> sentNoticesTo(String email) {
        ArgumentCaptor<PasswordChangedMail> captor = ArgumentCaptor.forClass(PasswordChangedMail.class);
        verify(mailer, atLeast(0)).sendPasswordChanged(captor.capture());
        return captor.getAllValues().stream().filter(mail -> mail.recipient().equalsIgnoreCase(email)).toList();
    }
}
