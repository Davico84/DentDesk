# ADR-0010: Contacto telefónico compartido y contacto de emergencia

- **Estado:** Aceptado (decisión); **pendiente de implementar** — comportamiento nuevo
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

🔮 **Comportamiento nuevo, no construido.** Hoy en `patients`:

- El **teléfono es opcional** (schema `optionalPhone`); no hay regla que lo exija.
- **No existe** la advertencia de "número compartido con otros pacientes" — se puede repetir un número sin aviso alguno (que es lo que este ADR quiere, pero **sin** el aviso).
- Ya existen campos de **contacto de emergencia** (`emergencyContactName/Phone/Relationship`), pero son **opcionales** y **no** hay regla "si no hay teléfono propio, el de emergencia es obligatorio".

**Se implementa vía el change `update-patients`.** Nota de diseño para ese change: la advertencia de número compartido exige que el backend pueda responder *"este número ya está en N pacientes de la clínica"* sin romper el aislamiento (es dato de la propia clínica, así que es correcto) — decidir si es un check en el alta o un aviso asíncrono.

## Alternativas descartadas

- **Teléfono único por paciente** (un número no puede repetirse) — rechazada: rompe el caso familiar (padre e hijo comparten número), que es habitual.
- **Teléfono siempre obligatorio** — rechazada: excluye a pacientes sin teléfono propio (adultos mayores); por eso la excepción del contacto de emergencia.
- **Bloquear el número compartido** en vez de advertir — rechazada: convierte una situación legítima en un error; basta con avisar.
