# ADR-0010: Contacto telefónico compartido y contacto de emergencia

- **Estado:** Aceptado — **implementado**
- **Fecha:** 2026-07-23
- **Decisores:** David Velarde Alvarado

## Contexto

En muchas familias varios pacientes **comparten un mismo número** telefónico (padre e hijo). También existen pacientes —adultos mayores— que **no poseen teléfono propio**. Un modelo que exija un teléfono único por paciente choca con estas situaciones reales.

## Decisión

- El número telefónico **no identifica** de forma única a un paciente: el sistema **permite compartir** el mismo número entre varios pacientes, mostrando solo una **advertencia** (no un bloqueo) de que el número ya está asociado a otros.
- Cuando un paciente **no posee teléfono propio**, se registra un **contacto de emergencia** (nombre, teléfono, relación) en su lugar.

Esto define la **excepción al celular obligatorio** que menciona [ADR-0005](0005-registro-minimo-paciente.md): el celular propio deja de ser estrictamente obligatorio si hay contacto de emergencia.

## Consecuencias

- ✅ Refleja situaciones reales del entorno clínico; evita restricciones artificiales.
- ⚠️ "Teléfono obligatorio salvo que haya contacto de emergencia" es una regla **condicional** (como la del responsable para menores): vive en el schema (paridad cliente/servidor), no en el componente.
- ⚠️ La advertencia de número compartido es **no bloqueante** — hay que distinguirla de un error de validación en la UI (es un aviso, deja continuar).

## Relación con lo construido

✅ **Implementado por completo**, vía el change `update-patients` (archivado: `openspec/changes/archive/2026-07-28-update-patients/`):

- El teléfono se puede **compartir** entre pacientes de la clínica; el sistema avisa **sin bloquear** (`PatientResponse.phoneSharedWithOtherPatients`, calculado excluyendo al propio paciente en edición).
- El **contacto de emergencia** (`emergencyContactName/Phone/Relationship`) pasa a **obligatorio solo si no hay teléfono propio** — regla condicional a nivel de objeto (`EmergencyContactRequiredWithoutPhone`, backend; `superRefine`, frontend), mismo patrón que el responsable de menores.
- La advertencia de número compartido es un dato **de la propia clínica** (respeta el aislamiento): se calcula server-side en cada alta/edición, no como aviso asíncrono.

## Alternativas descartadas

- **Teléfono único por paciente** (un número no puede repetirse) — rechazada: rompe el caso familiar (padre e hijo comparten número), que es habitual.
- **Teléfono siempre obligatorio** — rechazada: excluye a pacientes sin teléfono propio (adultos mayores); por eso la excepción del contacto de emergencia.
- **Bloquear el número compartido** en vez de advertir — rechazada: convierte una situación legítima en un error; basta con avisar.
