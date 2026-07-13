# ADR-0004: El paciente pertenece a una clínica (no a la plataforma)

- **Estado:** Aceptado
- **Fecha:** 2026-07-13
- **Decisores:** David Velarde Alvarado

## Contexto

En un SaaS multi-clínica hay que decidir **de quién es el paciente**. Una misma persona puede acudir a dos clínicas distintas de la plataforma, y el modelo tiene que responder: ¿es **un** registro compartido, o **uno por clínica**?

La pregunta parece técnica y no lo es: **decide qué sabe una clínica sobre otra**. Si el paciente fuera una entidad global (como el `User`), la clínica B podría descubrir que esa persona **ya es paciente de la clínica A** — con solo intentar darla de alta. Eso es una **fuga de privacidad** entre competidores, y ninguna clínica lo aceptaría.

Conviene fijarlo **antes de construir `patients`**, porque de esta decisión cuelgan `dental-chart`, `treatments`, `appointments`, `images` y `patient-billing`: todo el dominio clínico.

## Decisión

**El paciente pertenece a una clínica.** `Patient` es una entidad de negocio con `organizationId`, aislada como cualquier otra (`docs/backend.md §8.1`).

Una misma persona atendida en dos clínicas de la plataforma son **dos `Patient` distintos**, sin relación entre sí. Ninguna clínica puede saber que la otra la atiende.

- **Los datos demográficos se duplican** (nombre, documento, teléfono, dirección). **Es el precio, y se acepta**: es la única forma de que las clínicas no se filtren información.
- **La información clínica NUNCA se comparte**: odontograma, diagnósticos, tratamientos, notas, citas, imágenes y cobros pertenecen **a la clínica que los generó**.

## Consecuencias

- ✅ **Aislamiento total y trivial de implementar**: `Patient` sigue la regla general (`organizationId` + RLS). **No requiere código nuevo** — la decisión ya era consistente con lo construido; este ADR solo la hace explícita.
- ✅ **Privacidad entre clínicas**: ninguna descubre la cartera de pacientes de otra, ni siquiera por inferencia.
- ✅ **Autonomía**: cada clínica corrige o depura su ficha sin afectar a nadie.
- ⚠️ **Los datos demográficos divergen.** Si el paciente cambia de teléfono y lo actualiza en una clínica, **la otra no se entera**. Es correcto (son fichas independientes), pero hay que saberlo: **no hay "un" paciente que actualizar**.

### 🚨 Dos consecuencias concretas que hay que respetar

**1. La unicidad del documento (DNI) es POR CLÍNICA, no global.**

> La restricción va sobre **`(organizationId, document)`**, **nunca** sobre `document` a secas.

Declarar `document` como único global **rompe esta decisión**: la clínica B no podría dar de alta a un paciente que ya existe en la clínica A. Es un error fácil de cometer en una migración y que **no se nota hasta que un cliente no puede registrar a alguien**.

**2. El paciente que cambia de clínica pierde su historial.**

Su odontograma **no viaja**. Legalmente es lo correcto —los datos clínicos son de la clínica que los generó—, pero es una **decisión de producto**, no un efecto colateral: hay que asumirla conscientemente.

> 🚪 Deja abierta una capacidad futura: **portabilidad del historial con consentimiento explícito del paciente** (exportar/importar su ficha clínica de una clínica a otra). Sería una capacidad propia, con su propio flujo de consentimiento — **no un parche** sobre este modelo.

## Alternativas descartadas

- **Paciente global compartido entre clínicas** (una sola ficha, visible para todas las que lo atienden) — **rechazada**: rompe la privacidad entre clínicas (la B sabría que la A lo atiende), obliga a un modelo de permisos por-clínica-sobre-el-paciente que complica todo el aislamiento, y plantea preguntas legales sin respuesta clara (¿quién es el responsable del dato?, ¿quién lo puede corregir o borrar?).
- **Paciente global con enlace opcional y consentimiento** (un registro por clínica, pero vinculados si el paciente lo autoriza) — es esencialmente la **portabilidad** descrita arriba. Buena idea, **prematura para el MVP**: exige un flujo de consentimiento, resolución de conflictos entre fichas y un modelo de identidad del paciente que hoy no existe (el paciente **ni siquiera es usuario del sistema**, ver `docs/domain.md`).
