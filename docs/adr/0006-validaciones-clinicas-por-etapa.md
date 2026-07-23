# ADR-0006: Validaciones clínicas por etapa

- **Estado:** Aceptado (decisión); **pendiente** — su gate vive en `treatment-plans`, aún no construido
- **Fecha:** 2026-07-23
- **Decisores:** David Velarde Alvarado

## Contexto

No toda la información clínica es necesaria durante el registro inicial (ver [ADR-0005](0005-registro-minimo-paciente.md)). Algunos datos —alergias, antecedentes— solo son relevantes **antes de iniciar un procedimiento clínico**, no antes de admitir al paciente.

## Decisión

Las validaciones clínicas se ejecutan **según la etapa** del proceso, no todas en el alta:

- El **registro inicial** no exige información clínica avanzada.
- **Antes de iniciar un Plan de Tratamiento**, el sistema verifica que la información clínica requerida esté completa, y **alerta** para completarla si falta.

## Consecuencias

- ✅ Pide la información cuando de verdad se necesita → menos fricción en recepción.
- ⚠️ El "gate" no es del alta de paciente: pertenece al flujo de **crear un plan de tratamiento**. Cruza capacidades.

## Relación con lo construido

- La **anamnesis** (alergias, antecedentes, medicación) **ya existe** en `patients` — se guarda como JSONB en la fila del paciente, y el spec ya la describe *"de forma que **pueda alertarse** antes de un tratamiento"*. O sea: el **dato** está; el **enganche del dato** también.
- Lo que **falta** es el **gate por etapa**: la verificación "antes de crear el plan, exige anamnesis completa". Eso vive en la capacidad **`treatment-plans`** (fila 7 del roadmap), que **aún no está construida** del todo.

**Por tanto este ADR NO genera trabajo en `patients`.** Se registra aquí como decisión de dominio transversal; su implementación es responsabilidad de `treatment-plans` cuando se construya. No forma parte del change `update-patients`.

## Alternativas descartadas

- **Exigir toda la información clínica en el alta** — rechazada por la misma razón que [ADR-0005](0005-registro-minimo-paciente.md): mata la velocidad de admisión y mezcla etapas.
- **No validar nunca la completitud** (dejarlo al criterio del odontólogo) — rechazada: un plan de tratamiento sobre un paciente con alergias no registradas es un riesgo clínico que el sistema debe ayudar a evitar.
