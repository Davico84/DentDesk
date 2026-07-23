# ADR-0008: Identificador interno permanente del paciente

- **Estado:** Aceptado — **construido de facto**
- **Fecha:** 2026-07-23
- **Decisores:** David Velarde Alvarado

## Contexto

Los documentos de identidad **cambian**: se corrigen, se reemplazan (un extranjero pasa de pasaporte a carné de extranjería), varían de formato según el país. Un identificador que puede cambiar **no sirve** como clave técnica: si el documento fuera la PK, cambiarlo obligaría a re-enlazar historia clínica, citas, pagos y odontograma.

## Decisión

Cada paciente tiene un **identificador interno único y permanente** (`PatientId`), independiente de su documento. El **documento de identidad es un atributo** del paciente y puede actualizarse bajo las reglas del negocio, **sin** que nada de lo asociado (historia, citas, pagos, odontograma) se rompa.

## Consecuencias

- ✅ Desacopla la **identidad técnica** (estable) de la **identidad de negocio** (el documento, mutable).
- ✅ Corregir o cambiar el documento es una edición trivial: el `PatientId` no se mueve, así que nada colgado de él se ve afectado.

## Relación con lo construido

- ✅ **Ya es así.** La entidad `Patient` tiene su `id` propio (generado, estable) y el `document` es un **atributo mutable** — no es la clave. Todo lo que cuelga del paciente referencia el `id`, no el documento. Este ADR **documenta el racional** de una decisión ya vigente; no genera trabajo.
- Relacionado con [ADR-0009](0009-documento-unico-por-clinica.md) (unicidad del documento) y con [ADR-0004](0004-el-paciente-pertenece-a-una-clinica.md) (el paciente pertenece a una clínica): la unicidad del documento es **por clínica**, y va sobre el atributo, no sobre la PK.

## Alternativas descartadas

- **Usar el documento como clave primaria/identificador técnico** — rechazada: el documento cambia (correcciones, extranjeros que cambian de tipo), lo que obligaría a re-enlazar todo lo asociado y es una fuente de inconsistencias. Además, hay pacientes **sin** documento (un bebé), que no tendrían clave.
