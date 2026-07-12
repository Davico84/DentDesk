package com.odontorisas.tenancy;

import com.odontorisas.AbstractRlsIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Aislamiento de la pertenencia por IDENTIDAD (política real {@code membership_identity_isolation}
 * de V7), verificado como el rol de aplicación no-owner (RLS aplica de verdad).
 *
 * <p>Scenarios de multi-tenancy: "Un usuario solo ve sus propias membresías" y "Una consulta de
 * membresías que omite el filtro sigue estando aislada". Además: fail-closed sin contexto.
 */
@SpringBootTest
class MembershipRlsIT extends AbstractRlsIntegrationTest {

    /** Siembra (como owner, saltando RLS) un usuario + su organización + su membresía; devuelve el userId. */
    private long seedUserWithMembership(String email) throws SQLException {
        try (Connection su = ownerConnection()) {
            long userId;
            try (PreparedStatement st = su.prepareStatement(
                    "INSERT INTO users(email, password_hash, full_name, status) VALUES (?, 'x', 'Test', 'ACTIVE') RETURNING id")) {
                st.setString(1, email);
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    userId = rs.getLong(1);
                }
            }
            long orgId;
            try (PreparedStatement st = su.prepareStatement(
                    "INSERT INTO organizations(name, slug, type, status) VALUES ('Org', ?, 'CLINIC', 'ACTIVE') RETURNING id")) {
                st.setString(1, "org-" + email.replaceAll("[^a-zA-Z0-9]", "-"));
                try (ResultSet rs = st.executeQuery()) {
                    rs.next();
                    orgId = rs.getLong(1);
                }
            }
            try (PreparedStatement st = su.prepareStatement(
                    "INSERT INTO memberships(user_id, organization_id, role, status) VALUES (?, ?, 'ADMIN', 'ACTIVE')")) {
                st.setLong(1, userId);
                st.setLong(2, orgId);
                st.executeUpdate();
            }
            return userId;
        }
    }

    private Set<Long> membershipUserIdsVisibleTo(long userId) throws SQLException {
        try (Connection app = appConnection(); Statement s = app.createStatement()) {
            s.execute("SELECT set_config('app.user_id', '" + userId + "', false)");
            Set<Long> visible = new HashSet<>();
            try (ResultSet rs = s.executeQuery("SELECT user_id FROM memberships")) {
                while (rs.next()) {
                    visible.add(rs.getLong(1));
                }
            }
            return visible;
        }
    }

    @Test
    void a_user_only_sees_its_own_memberships() throws SQLException {
        long userA = seedUserWithMembership("rls-a-" + System.nanoTime() + "@test.dev");
        long userB = seedUserWithMembership("rls-b-" + System.nanoTime() + "@test.dev");

        Set<Long> visibleToA = membershipUserIdsVisibleTo(userA);

        assertThat(visibleToA).containsExactly(userA);
        assertThat(visibleToA).doesNotContain(userB);
    }

    @Test
    void a_query_without_user_filter_is_still_isolated() throws SQLException {
        long userA = seedUserWithMembership("rls-c-" + System.nanoTime() + "@test.dev");
        seedUserWithMembership("rls-d-" + System.nanoTime() + "@test.dev");

        // La consulta NO lleva WHERE user_id; el motor la restringe igualmente a la identidad del contexto.
        Set<Long> visibleToA = membershipUserIdsVisibleTo(userA);

        assertThat(visibleToA).containsExactly(userA);
    }

    @Test
    void a_query_without_identity_context_fails_closed() throws SQLException {
        seedUserWithMembership("rls-e-" + System.nanoTime() + "@test.dev");

        try (Connection app = appConnection(); Statement s = app.createStatement()) {
            // Sin app.user_id fijado, current_setting (sin missing_ok) lanza → fallo ruidoso.
            assertThatThrownBy(() -> s.executeQuery("SELECT user_id FROM memberships"))
                .isInstanceOf(SQLException.class);
        }
    }
}
