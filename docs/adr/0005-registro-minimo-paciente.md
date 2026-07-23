# ADR-0005: Registro inicial de paciente con datos mínimos

- **Estado:** Aceptado (decisión); **pendiente de implementar** — ver "Relación con lo construido"
- **Fecha:** 2026-07-23
- **Decisores:** David Velarde Alvarado

## Contexto

Durante el registro de un nuevo paciente, la recepcionista normalmente solo dispone de información básica. Solicitar toda la información clínica (alergias, antecedentes, dirección completa) incrementa el tiempo de atención y retrasa la admisión. Un formulario largo hace perder pacientes.

## Decisión

El sistema permite registrar un paciente con la información **mínima necesaria** para identificarlo y habilitar su atención. El resto (anamnesis, dirección, etc.) se completa **después**, durante la consulta clínica.

Conjunto de datos que la clínica considera **necesario** para identificar y atender:

- Nombres
- Apellidos
- Tipo de documento
- Número de documento
- Sexo
- Fecha de nacimiento
- Celular (con las excepciones de [ADR-0010](0010-contacto-telefonico-compartido.md))

## Consecuencias

- ✅ Reduce el tiempo de admisión; separa el proceso administrativo del clínico.
- ✅ La anamnesis se completa cuando de verdad hace falta (ver [ADR-0006](0006-validaciones-clinicas-por-etapa.md)).
- ⚠️ Endurece el alta respecto a lo construido hoy (ver abajo): hay que reconciliar el caso del **paciente sin documento** (bebé) que hoy está permitido.

## Relación con lo construido

⚠️ **Diverge del spec `patients` ya construido y testeado.** Hoy:

- El **mínimo real** es **solo `nombre` + `fecha de nacimiento`** (`openspec/specs/patients/spec.md`, scenario "Alta con los datos mínimos"; schema Zod `patientSchema`).
- El **documento es opcional** — hay un scenario explícito y testeado: *"El documento es opcional (un bebé, por ejemplo)"*.
- **Sexo** y **teléfono** son **opcionales** en el schema.

Este ADR, tal como está escrito, **revierte** esas tres decisiones. Además tiene una **tensión interna**: la casuística del propio ADR (el flujo real de recepción) solo usa *nombre, documento, fecha, celular* — no apellidos separados ni sexo. Y no contempla el bebé sin documento.

**Se implementa vía el change `update-patients`** (propuesto junto a este ADR). Las reconciliaciones (¿se mantiene la excepción del bebé sin documento?, ¿sexo obligatorio o no?, ¿apellidos separados de nombres?) se resuelven en la **revisión humana** de ese change, no aquí.

## Alternativas descartadas

- **Formulario completo en el alta** (toda la anamnesis y datos clínicos de entrada) — rechazada: es la causa directa del tiempo de admisión alto y de perder pacientes; mezcla el proceso administrativo con el clínico.
