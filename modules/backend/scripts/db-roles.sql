-- Provisión de roles de base de datos (menor privilegio) para OdontoRisas.
-- Capacidad: multi-tenancy (design.md D7, Open Question 6).
--
-- Objetivo: que la aplicación NO corra como propietario de las tablas, para que
-- Row-Level Security le aplique de forma ineludible y una inyección SQL no pueda
-- ejecutar `ALTER TABLE ... DISABLE ROW LEVEL SECURITY`.
--
-- Ejecutar UNA vez por entorno, como superusuario, antes del primer arranque:
--   psql -U postgres -d odontorisas -v app_pwd='...' -v admin_pwd='...' -f scripts/db-roles.sql
--
-- Roles:
--   odontorisas_admin → propietario de las tablas; lo usa SOLO Flyway (DDL). NOLOGIN opcional
--                       si Flyway usa otra vía; aquí LOGIN para conexión directa.
--   odontorisas_app   → runtime de la aplicación; SOLO DML; NO owner; NO SUPERUSER; NO BYPASSRLS.
--
-- Idempotente: no falla si los roles ya existen.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_admin') THEN
        EXECUTE format('CREATE ROLE odontorisas_admin LOGIN PASSWORD %L', :'admin_pwd');
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'odontorisas_app') THEN
        -- NOSUPERUSER NOBYPASSRLS son el default, se explicitan como recordatorio.
        EXECUTE format('CREATE ROLE odontorisas_app LOGIN NOSUPERUSER NOBYPASSRLS PASSWORD %L', :'app_pwd');
    END IF;
END $$;

-- La base y el esquema deben pertenecer al admin (Flyway crea las tablas como admin).
-- Ajusta el nombre de base si no es `odontorisas`.
ALTER DATABASE odontorisas OWNER TO odontorisas_admin;
ALTER SCHEMA public OWNER TO odontorisas_admin;

-- La app necesita conectarse y usar el esquema; los GRANT de tablas/secuencias los
-- aplica la migración V6 (y ALTER DEFAULT PRIVILEGES cubre las tablas futuras).
GRANT CONNECT ON DATABASE odontorisas TO odontorisas_app;
GRANT USAGE ON SCHEMA public TO odontorisas_app;
