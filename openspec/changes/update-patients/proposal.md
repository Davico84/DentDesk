## Why

Una tanda de ADRs sobre pacientes (`docs/adr/0005`–`0010`) fijó decisiones de dominio; algunas ya están construidas (`0007` núcleo, `0008`, `0009` núcleo), otras **divergen** de la capacidad `patients` ya enviada y testeada y necesitan implementarse. Este change recoge las divergencias **implementables sobre `patients`**:

- **ADR-0010** — el teléfono se puede **compartir** entre pacientes (familias); hoy se puede repetir pero **sin aviso**. Y un paciente **sin teléfono propio** (adulto mayor) debe registrar un **contacto de emergencia** en su lugar; hoy el contacto de emergencia existe pero es opcional y sin esa regla.
- **ADR-0009 (extensión)** — al chocar con un documento ya registrado, además de rechazar, **ofrecer abrir la ficha existente**; hoy solo rechaza con un `409`.

Quedan **fuera** (no son de `patients`): ADR-0006 (gate por etapa → `treatment-plans`), la parte de consentimientos de ADR-0007 (→ `treatments`/consentimientos), y ADR-0008 (ya construido).

## What Changes

- El sistema SHALL **avisar** (sin bloquear) cuando el teléfono de un paciente ya está asociado a otros pacientes de la misma clínica.
- El sistema SHALL exigir un **contacto de emergencia** cuando el paciente **no** registra teléfono propio (regla condicional, como la del responsable para menores).
- Cuando el alta (o la edición) choca con un documento ya usado en la clínica, la respuesta SHALL permitir al cliente **ofrecer abrir la ficha existente** (identificándola), además de rechazar el duplicado.

## Capabilities

### New Capabilities
<!-- ninguna -->

### Modified Capabilities
- `patients`: se **añaden** requirements sobre el contacto telefónico (compartido + contacto de emergencia obligatorio si no hay teléfono propio) y sobre ofrecer la ficha existente ante un documento duplicado.

## Open Questions (requieren decisión humana antes de implementar)

- **ADR-0005 (endurecer los datos mínimos) NO se especifica aún en este change.** Tal como está escrito, revierte comportamiento **construido y testeado**: hoy el mínimo es *nombre + fecha de nacimiento*, con **documento, sexo y teléfono opcionales** (incluido el scenario testeado "documento opcional — un bebé"). Antes de escribir su delta hay que resolver:
  1. ¿Qué campos pasan a **obligatorios** exactamente? El ADR lista 7 pero su propia casuística usa 4 (omite apellidos y sexo).
  2. ¿Sobrevive la **excepción del bebé sin documento**? Un recién nacido no tiene documento; hacerlo obligatorio lo excluye.
  3. ¿`fullName` se parte en **nombres + apellidos** separados? Hoy es un solo campo.
  Estas son decisiones de producto reservadas al revisor humano; una vez resueltas, se añade el delta correspondiente.

## Impact

- **Backend**: validación condicional del contacto de emergencia; endpoint/respuesta de conflicto de documento que identifique la ficha existente (para que el front pueda enlazarla); consulta "¿este teléfono ya está en otros pacientes de la clínica?" (dato de la propia clínica, respeta el aislamiento).
- **Frontend**: aviso no bloqueante de teléfono compartido; regla condicional de contacto de emergencia en el schema Zod (paridad con backend); en el `409` de documento, ofrecer navegar a la ficha existente.
- **Tests**: uno por Scenario nuevo (back = slices/IT; front = Vitest/schema).
- **Docs**: al archivar, marcar en `docs/vision.md` la extensión de `patients` y actualizar `docs/domain.md` (regla de contacto de emergencia).
