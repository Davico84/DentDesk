# ADR-0009: Documento de identidad único por clínica

- **Estado:** Aceptado — **implementado por completo** (unicidad + "abrir registro existente")
- **Fecha:** 2026-07-23
- **Decisores:** David Velarde Alvarado

## Contexto

Un mismo paciente no debe registrarse múltiples veces con el mismo documento **dentro de una clínica**. Pero el documento **puede modificarse** cuando exista una razón válida (corrección, cambio de tipo). La unicidad es **por clínica**, nunca global — ver [ADR-0004](0004-el-paciente-pertenece-a-una-clinica.md): una persona puede ser paciente de dos clínicas distintas.

## Decisión

La combinación **(tipo de documento, número de documento)** es **única dentro de una misma clínica**.

Cuando la recepcionista intenta registrar un documento que ya existe en su clínica, el sistema **impide crear un duplicado** y **ofrece abrir el registro existente** para actualizarlo:

> "Ya existe un paciente registrado con este documento."
> Opciones: **Abrir registro existente** · **Cancelar**.

## Consecuencias

- ✅ Evita duplicados confirmados y las inconsistencias que arrastran (dos fichas para la misma persona).
- ✅ El aviso **con salida a la ficha existente** convierte un error en un atajo útil.

## Relación con lo construido

- ✅ **Construido:** la unicidad **(organización, tipo, número)** ya está — y correctamente **por clínica** (`patients` design.md D8, `ADR-0004`). Hay scenarios testeados: no se puede dar de alta ni editar a un documento que ya existe en la clínica, **incluidos los pacientes dados de baja**. El mismo documento **sí** puede existir en otra clínica.
- ✅ **Construido:** "ofrecer abrir el registro existente" — el change `update-patients` (archivado: `openspec/changes/archive/2026-07-28-update-patients/`) lo cerró. El `409` de documento duplicado ahora identifica la ficha existente (`existingPatientId`) y el cliente ofrece "Abrir ficha existente" con navegación directa.

## Alternativas descartadas

- **Documento único global** (a secas, no por clínica) — rechazada: rompe [ADR-0004](0004-el-paciente-pertenece-a-una-clinica.md); la clínica B no podría registrar a alguien que ya existe en la A, y se filtraría que la A lo atiende.
- **Permitir duplicados y deduplicar después** — rechazada: las inconsistencias (historia clínica partida entre dos fichas) son caras de reparar; es mejor impedirlas en el alta.
