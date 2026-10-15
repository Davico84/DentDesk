-- Corrige V24: la rama "ADMIN gestiona su organización" leía app.organization_id de forma estricta,
-- pero memberships también se consulta con solo la IDENTIDAD fijada (login, refresh, registro:
-- todavía no hay organización activa). Sin el parámetro, current_setting() lanzaba error y rompía
-- esas lecturas en vez de limitarse a no conceder la rama de administrador.
--
-- app.user_id sigue estricto (fail-closed: sin identidad no se ve nada). app.organization_id se lee
-- con missing_ok y '' -> NULL (un set_config local deja '' al terminar la transacción): sin
-- organización activa la comparación es NULL y la rama ADMIN simplemente no aplica.
DROP POLICY membership_identity_isolation ON memberships;
CREATE POLICY membership_identity_isolation ON memberships
    USING (
        user_id = current_setting('app.user_id')::BIGINT
        OR (
            organization_id = NULLIF(current_setting('app.organization_id', true), '')::BIGINT
            AND current_user_is_org_admin(organization_id)
        )
    );
