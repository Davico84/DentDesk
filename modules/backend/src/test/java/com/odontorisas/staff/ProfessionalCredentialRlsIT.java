package com.odontorisas.staff;

import com.odontorisas.AbstractRlsIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garantías de la base sobre la credencial profesional y el retiro de la capacidad clínica
 * ({@code update-staff-professional-credential} design.md D1/D2, migración V30), verificadas como el
 * rol de aplicación no-owner, que es el único al que la RLS se le aplica de verdad.
 *
 * <p>Scenarios de {@code staff}: <i>Intentar declarar la credencial de otro usuario</i>,
 * <i>Credencial de alguien de otra clínica</i>, <i>Número declarado por otro usuario</i> (la
 * unicidad choca aunque la otra fila sea invisible), <i>Un miembro intenta levantar su propio
 * retiro</i> y <i>Una membresía no nace retirada</i>.
 */
@SpringBootTest
class ProfessionalCredentialRlsIT extends AbstractRlsIntegrationTest {

    private long clinicA;
    private long clinicB;
    private long adminA;
    private long otherAdminA;
    private long memberA;
    private long memberB;
    private long noCredential;
    private String copMemberA;
    private String copMemberB;

    @BeforeEach
    void twoClinicsWithCredentials() throws SQLException {
        String suffix = UUID.randomUUID().toString();
        // Números únicos por corrida: el índice único es global y las clases comparten la base.
        long base = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L);
        copMemberA = String.valueOf(base);
        copMemberB = String.valueOf(base + 1);
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            clinicA = id(s, "INSERT INTO organizations (name, slug) VALUES ('A', 'cred-a-" + suffix + "') RETURNING id");
            clinicB = id(s, "INSERT INTO organizations (name, slug) VALUES ('B', 'cred-b-" + suffix + "') RETURNING id");
            adminA = user(s, "admin-a-" + suffix);
            otherAdminA = user(s, "admin2-a-" + suffix);
            memberA = user(s, "member-a-" + suffix);
            memberB = user(s, "member-b-" + suffix);
            noCredential = user(s, "nocred-" + suffix);
            membership(s, adminA, clinicA, "ADMIN");
            membership(s, otherAdminA, clinicA, "ADMIN");
            membership(s, memberA, clinicA, "MEMBER");
            membership(s, memberB, clinicB, "MEMBER");
            membership(s, noCredential, clinicA, "MEMBER");
            credential(s, memberA, copMemberA);
            credential(s, memberB, copMemberB);
        }
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static long user(Statement s, String name) throws SQLException {
        return id(s, "INSERT INTO users (email, password_hash) VALUES ('" + name + "@test.local', 'x') RETURNING id");
    }

    private static void membership(Statement s, long user, long org, String role) throws SQLException {
        s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES ("
            + user + ", " + org + ", '" + role + "', 'ACTIVE')");
    }

    private static void credential(Statement s, long user, String cop) throws SQLException {
        s.execute("INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) VALUES ("
            + user + ", '" + cop + "', current_date + 30, now())");
    }

    /** Conexión del rol de app con la identidad y (opcional) la clínica activa fijadas. */
    private Connection as(long user, Long activeOrg) throws SQLException {
        Connection c = appConnection();
        try (Statement s = c.createStatement()) {
            s.execute("SELECT set_config('app.user_id', '" + user + "', false)");
            if (activeOrg != null) {
                s.execute("SELECT set_config('app.organization_id', '" + activeOrg + "', false)");
            }
        }
        return c;
    }

    private Set<Long> credentialsVisibleTo(long user, Long activeOrg) throws SQLException {
        try (Connection c = as(user, activeOrg); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT user_id FROM professional_credential")) {
            Set<Long> visible = new HashSet<>();
            while (rs.next()) {
                visible.add(rs.getLong(1));
            }
            return visible;
        }
    }

    private String copOf(long user) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT cop_number FROM professional_credential WHERE user_id = " + user)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static void assertRejected(Connection c, String sql, String sqlState) {
        assertThatThrownBy(() -> {
            try (Statement s = c.createStatement()) {
                s.execute(sql);
            }
        }).isInstanceOf(SQLException.class)
            .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo(sqlState));
    }

    private static int update(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            return s.executeUpdate(sql);
        }
    }

    private String revokedAtOf(long user, long org) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery("SELECT clinical_capacity_revoked_at::text FROM memberships WHERE user_id = "
                 + user + " AND organization_id = " + org)) {
            rs.next();
            return rs.getString(1);
        }
    }

    // --- Lectura -------------------------------------------------------------------------------

    @Test
    void a_user_sees_only_its_own_credential() throws SQLException {
        assertThat(credentialsVisibleTo(memberA, clinicA)).containsExactly(memberA);
    }

    @Test
    void the_admin_sees_credentials_of_its_clinic_members_only() throws SQLException {
        assertThat(credentialsVisibleTo(adminA, clinicA)).contains(memberA).doesNotContain(memberB);
    }

    @Test
    void without_an_active_clinic_the_admin_branch_does_not_apply() throws SQLException {
        assertThat(credentialsVisibleTo(adminA, null)).doesNotContain(memberA, memberB);
    }

    // --- Escritura de la credencial ------------------------------------------------------------

    @Test
    void nobody_inserts_the_credential_of_another_user_not_even_the_admin() throws SQLException {
        try (Connection c = as(adminA, clinicA)) {
            // 42501: la fila nueva no pasa el WITH CHECK de la policy de INSERT.
            assertRejected(c, "INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) "
                + "VALUES (" + noCredential + ", '7', current_date, now())", "42501");
        }
    }

    @Test
    void nobody_updates_the_credential_of_another_user_not_even_the_admin() throws SQLException {
        try (Connection c = as(adminA, clinicA)) {
            // El admin VE la fila (rama de lectura), pero el USING del UPDATE no se la deja tocar.
            assertThat(update(c, "UPDATE professional_credential SET cop_number = '1' WHERE user_id = " + memberA))
                .isZero();
        }
        assertThat(copOf(memberA)).isEqualTo(copMemberA);
    }

    @Test
    void an_update_cannot_take_over_a_foreign_row_by_rewriting_its_user_id() throws SQLException {
        try (Connection c = as(noCredential, clinicA)) {
            assertThat(update(c, "UPDATE professional_credential SET user_id = " + noCredential
                + " WHERE user_id = " + memberA)).isZero();
        }
        assertThat(copOf(memberA)).isEqualTo(copMemberA);
        assertThat(copOf(noCredential)).isNull();
    }

    @Test
    void a_user_cannot_hand_its_own_row_to_another_user() throws SQLException {
        try (Connection c = as(memberA, clinicA)) {
            assertRejected(c, "UPDATE professional_credential SET user_id = " + noCredential
                + " WHERE user_id = " + memberA, "42501");
        }
    }

    @Test
    void a_user_renews_its_own_credential() throws SQLException {
        try (Connection c = as(memberA, clinicA)) {
            assertThat(update(c, "UPDATE professional_credential SET licensed_until = current_date + 365 "
                + "WHERE user_id = " + memberA)).isEqualTo(1);
        }
    }

    @Test
    void the_app_cannot_delete_credentials() throws SQLException {
        try (Connection c = as(memberA, clinicA)) {
            // Sin policy de DELETE (y en producción, sin el privilegio): no se borra nada.
            assertThat(update(c, "DELETE FROM professional_credential WHERE user_id = " + memberA)).isZero();
        }
        assertThat(copOf(memberA)).isEqualTo(copMemberA);
    }

    @Test
    void a_duplicate_cop_collides_even_when_the_other_row_is_invisible() throws SQLException {
        assertThat(credentialsVisibleTo(noCredential, clinicA)).doesNotContain(memberB);
        try (Connection c = as(noCredential, clinicA)) {
            assertRejected(c, "INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) "
                + "VALUES (" + noCredential + ", '" + copMemberB + "', current_date, now())", "23505");
        }
    }

    @Test
    void the_cop_number_only_accepts_digits() throws SQLException {
        try (Connection c = as(noCredential, clinicA)) {
            assertRejected(c, "INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) "
                + "VALUES (" + noCredential + ", '12A4', current_date, now())", "23514");
        }
    }

    // --- Retiro en la membresía ----------------------------------------------------------------

    private void revokeAsAdmin(long member) throws SQLException {
        try (Connection c = as(adminA, clinicA)) {
            assertThat(update(c, "UPDATE memberships SET clinical_capacity_revoked_at = now(), "
                + "clinical_capacity_revoked_by = " + adminA + " WHERE user_id = " + member
                + " AND organization_id = " + clinicA)).isEqualTo(1);
        }
    }

    @Test
    void the_admin_revokes_and_restores_a_member() throws SQLException {
        revokeAsAdmin(memberA);
        assertThat(revokedAtOf(memberA, clinicA)).isNotNull();

        try (Connection c = as(adminA, clinicA)) {
            assertThat(update(c, "UPDATE memberships SET clinical_capacity_revoked_at = NULL, "
                + "clinical_capacity_revoked_by = NULL WHERE user_id = " + memberA
                + " AND organization_id = " + clinicA)).isEqualTo(1);
        }
        assertThat(revokedAtOf(memberA, clinicA)).isNull();
    }

    @Test
    void a_member_cannot_lift_its_own_revocation_with_direct_sql() throws SQLException {
        revokeAsAdmin(memberA);
        try (Connection c = as(memberA, clinicA)) {
            assertRejected(c, "UPDATE memberships SET clinical_capacity_revoked_at = NULL, "
                + "clinical_capacity_revoked_by = NULL WHERE user_id = " + memberA, "42501");
        }
        assertThat(revokedAtOf(memberA, clinicA)).isNotNull();
    }

    @Test
    void a_member_cannot_change_role_and_revocation_at_once() throws SQLException {
        revokeAsAdmin(memberA);
        try (Connection c = as(memberA, clinicA)) {
            assertRejected(c, "UPDATE memberships SET role = 'MEMBER', clinical_capacity_revoked_at = NULL, "
                + "clinical_capacity_revoked_by = NULL WHERE user_id = " + memberA, "42501");
        }
    }

    @Test
    void the_admin_cannot_revoke_itself_or_another_admin() throws SQLException {
        try (Connection c = as(adminA, clinicA)) {
            assertRejected(c, "UPDATE memberships SET clinical_capacity_revoked_at = now(), "
                + "clinical_capacity_revoked_by = " + adminA + " WHERE user_id = " + adminA, "42501");
            assertRejected(c, "UPDATE memberships SET clinical_capacity_revoked_at = now(), "
                + "clinical_capacity_revoked_by = " + adminA + " WHERE user_id = " + otherAdminA
                + " AND organization_id = " + clinicA, "42501");
        }
    }

    @Test
    void a_membership_cannot_be_created_already_revoked() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            long fresh = user(s, "fresh-" + UUID.randomUUID());
            assertThatThrownBy(() -> s.execute("INSERT INTO memberships (user_id, organization_id, role, status, "
                + "clinical_capacity_revoked_at, clinical_capacity_revoked_by) VALUES (" + fresh + ", " + clinicA
                + ", 'MEMBER', 'ACTIVE', now(), " + adminA + ")"))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
        }
    }

    @Test
    void a_status_change_that_does_not_touch_the_revocation_still_works() throws SQLException {
        try (Connection c = as(adminA, clinicA)) {
            assertThat(update(c, "UPDATE memberships SET status = 'INACTIVE' WHERE user_id = " + memberA
                + " AND organization_id = " + clinicA)).isEqualTo(1);
        }
    }

    @Test
    void revoked_at_and_revoked_by_go_together() throws SQLException {
        try (Connection c = as(adminA, clinicA)) {
            assertRejected(c, "UPDATE memberships SET clinical_capacity_revoked_at = now() WHERE user_id = "
                + memberA + " AND organization_id = " + clinicA, "23514");
        }
    }
}
