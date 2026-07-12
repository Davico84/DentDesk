-- Capacidad: multi-tenancy — Row-Level Security de IDENTIDAD sobre la tabla puente.
--
-- `memberships` no se aísla por organización (se consulta por user_id, antes de haber
-- una organización en contexto), sino por IDENTIDAD: cada quien solo ve/escribe sus
-- propias membresías. Es la primera política RLS real del sistema.
--
-- FORCE: aplica la política TAMBIÉN al propietario de la tabla; con un solo datasource
-- el owner podría saltarse RLS. (Un rol SUPERUSER/BYPASSRLS igual la saltaría: por eso
-- el rol de aplicación en despliegue no debe tener esos atributos — ver docs/architecture.md.)
ALTER TABLE memberships ENABLE ROW LEVEL SECURITY;
ALTER TABLE memberships FORCE ROW LEVEL SECURITY;

-- Sin `WITH CHECK` explícito, la expresión de `USING` gobierna también INSERT/UPDATE:
-- una escritura solo se permite si su user_id coincide con la identidad del contexto.
-- Sin `missing_ok`: si `app.user_id` no está fijado, `current_setting` LANZA (fail-closed
-- ruidoso), lo que delata una consulta fuera de una transacción con contexto.
CREATE POLICY membership_identity_isolation ON memberships
    USING (user_id = current_setting('app.user_id')::bigint);
