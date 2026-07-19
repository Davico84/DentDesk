-- Capacidad: update-patients (autoría) — aplica el mecanismo de autoría automática de
-- update-multi-tenancy (AuditorAware/@EnableJpaAuditing) a la primera entidad de negocio.
--
-- NULL-ables (design.md D2): los pacientes ya existentes no tienen autoría real que
-- backfillear — inventarla sería peor que no tenerla. Los pacientes nuevos sí la tendrán
-- siempre (el mecanismo automático la rellena en cada INSERT).
--
-- ON DELETE SET NULL, no CASCADE (design.md D3): el paciente no PERTENECE al usuario que lo
-- creó, el usuario es solo su autor. Un CASCADE aquí borraría fichas de pacientes si algún día
-- se elimina un usuario — justo lo que el spec de patients prohíbe ("dar de baja NO borra").

ALTER TABLE patients
    ADD COLUMN created_by BIGINT REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN updated_by BIGINT REFERENCES users (id) ON DELETE SET NULL;
