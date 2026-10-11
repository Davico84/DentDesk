-- Provisión de roles de base de datos (menor privilegio) para OdontoRisas.
-- Capacidad: multi-tenancy (design.md D7, Open Question 6).
--
-- Objetivo: que la aplicación NO corra como propietario de las tablas, para que
-- Row-Level Security le aplique de forma ineludible y una inyección SQL no pueda
-- ejecutar `ALTER TABLE ... DISABLE ROW LEVEL SECURITY`.
--
-- Ejecutar UNA vez por entorno, antes del primer arranque, con un rol que pueda crear
-- roles (superusuario en local; el dueño del proyecto en Neon):
--   psql -v ON_ERROR_STOP=1 -v app_pwd='...' -v admin_pwd='...' [-v db_name=...] \
--        -d <base> -f scripts/db-roles.sql
-- `db_name` es opcional (por defecto `odontorisas`).
--
-- En el SQL Editor de Neon (no entiende las órdenes `\` de psql) se usa la versión en SQL
-- plano de docs/deployment.md §4.2.
--
-- Roles:
--   odontorisas_admin → propietario de las tablas; lo usa SOLO Flyway (DDL).
--   odontorisas_app   → runtime de la aplicación; SOLO DML; NO owner; NO SUPERUSER; NO BYPASSRLS.
--
-- Idempotente: no falla si los roles ya existen.
--
-- ⚠️ Las variables de psql (`:'admin_pwd'`) NO se reemplazan dentro de un bloque `DO $$ … $$`
-- (es un literal). Por eso los CREATE ROLE se arman con `format(...)` en un SELECT normal y se
-- ejecutan con `\gexec`, que solo corre las filas devueltas (si el rol ya existe, ninguna).

\if :{?db_name}
\else
  \set db_name odontorisas
\endif

SELECT format('CREATE ROLE odontorisas_admin LOGIN PASSWORD %L', :'admin_pwd')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_admin')
\gexec

-- NOSUPERUSER NOBYPASSRLS son el default, se explicitan como recordatorio: con cualquiera de
-- los dos, RLS deja de aplicar y cada clínica vería los datos de las otras.
SELECT format('CREATE ROLE odontorisas_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD %L', :'app_pwd')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_app')
\gexec

-- Para traspasar la propiedad, quien ejecuta debe poder "ser" el nuevo dueño. Desde PostgreSQL 16
-- crear un rol no da esa membresía (en Neon el dueño del proyecto no es superusuario), así que se
-- concede explícitamente. Para un superusuario es inocuo.
GRANT odontorisas_admin TO CURRENT_USER;

-- La base y el esquema pertenecen al admin (Flyway crea las tablas como admin).
ALTER DATABASE :"db_name" OWNER TO odontorisas_admin;
ALTER SCHEMA public OWNER TO odontorisas_admin;

-- La app necesita conectarse y usar el esquema; los GRANT de tablas/secuencias los
-- aplica la migración V6 (y ALTER DEFAULT PRIVILEGES cubre las tablas futuras).
GRANT CONNECT ON DATABASE :"db_name" TO odontorisas_app;
GRANT USAGE ON SCHEMA public TO odontorisas_app;
