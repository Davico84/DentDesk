package com.odontorisas;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Siembra, como owner, lo que necesita alguien para firmar actos clínicos ({@code
 * update-staff-professional-credential}): una membresía activa en la clínica y una credencial vigente.
 * Desde V30, cerrar la admisión y sellar una nota lo exigen, así que los tests de esas operaciones
 * preparan a su usuario con esto.
 */
public final class ClinicalSignerFixtures {

    private ClinicalSignerFixtures() {
    }

    /** Número COP único por llamada: el índice único es global y los tests comparten la base. */
    public static String uniqueCop() {
        return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L));
    }

    /**
     * Deja a {@code userId} habilitado para firmar en {@code organizationId}: crea la membresía si no
     * existe (como MEMBER) y declara una credencial vigente por un año, o la renueva si ya tenía una.
     */
    public static void enableSigner(Connection owner, long userId, long organizationId) throws SQLException {
        try (Statement s = owner.createStatement()) {
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) SELECT " + userId + ", "
                + organizationId + ", 'MEMBER', 'ACTIVE' WHERE NOT EXISTS (SELECT 1 FROM memberships WHERE user_id = "
                + userId + " AND organization_id = " + organizationId + ")");
            s.execute("INSERT INTO professional_credential (user_id, cop_number, licensed_until, sworn_at) VALUES ("
                + userId + ", '" + uniqueCop() + "', current_date + 365, now()) "
                + "ON CONFLICT (user_id) DO UPDATE SET licensed_until = current_date + 365");
        }
    }
}
