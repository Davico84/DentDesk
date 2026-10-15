-- Un administrador puede gestionar las membresías de su propia organización.
-- La función es SECURITY DEFINER para consultar memberships sin recursión de RLS;
-- el contexto de identidad/organización sigue viniendo exclusivamente del token.
CREATE OR REPLACE FUNCTION current_user_is_org_admin(target_org_id BIGINT)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM memberships m
        WHERE m.user_id = current_setting('app.user_id')::BIGINT
          AND m.organization_id = target_org_id
          AND m.role = 'ADMIN'
          AND m.status = 'ACTIVE'
    )
$$;

DROP POLICY membership_identity_isolation ON memberships;
CREATE POLICY membership_identity_isolation ON memberships
    USING (
        user_id = current_setting('app.user_id')::BIGINT
        OR (
            organization_id = current_setting('app.organization_id')::BIGINT
            AND current_user_is_org_admin(organization_id)
        )
    );
