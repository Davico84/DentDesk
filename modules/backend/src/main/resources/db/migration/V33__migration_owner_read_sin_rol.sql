-- Política de solo lectura del dueño de las tablas (V32, add-appointments design.md D4) sin nombrar un rol.
--
-- V32 la creó `TO CURRENT_USER`, que PostgreSQL resuelve al crearla: el catálogo guarda el rol que migró y
-- pg_dump la escribe como `TO odontorisas_admin`. Una copia restaurada en una base sin ese rol (la
-- verificación diaria de db-backup.yml, una PostgreSQL local) abortaba en pg_restore. Ahora aplica a PUBLIC
-- y la condición compara el rol actual con el dueño de la tabla, leído del catálogo al evaluar: después de
-- restaurar con --no-owner, el dueño es quien restauró y la política sigue siendo "el dueño lee".
--
-- Mismo efecto que V32, un poco más estricto: antes también la recibía un miembro de odontorisas_admin
-- (herencia de roles); ahora solo el rol dueño exacto. El rol de la app no es el dueño: para él la condición
-- es falsa y lo que ve directamente no cambia. `clinic_capacity_facts` (SECURITY DEFINER) corre como el
-- dueño, así que sigue leyendo las filas de su clínica. La subconsulta no depende de la fila: se evalúa una
-- vez por consulta.

DROP POLICY migration_owner_read ON memberships;
DROP POLICY migration_owner_read ON professional_credential;

CREATE POLICY migration_owner_read ON memberships FOR SELECT
    USING (current_user = (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = 'public.memberships'::regclass));
CREATE POLICY migration_owner_read ON professional_credential FOR SELECT
    USING (current_user = (SELECT pg_get_userbyid(relowner) FROM pg_class WHERE oid = 'public.professional_credential'::regclass));
