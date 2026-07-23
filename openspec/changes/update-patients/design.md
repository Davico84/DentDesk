## Context

`patients` está construida y testeada. Las reglas de contacto y de documento hoy:

- **Teléfono opcional** (`optionalPhone` en `patientSchema`); se puede repetir el mismo número entre pacientes **sin aviso**.
- **Contacto de emergencia** (`emergencyContactName/Phone/Relationship`) existe pero es **opcional**, sin ninguna regla que lo ligue a la ausencia de teléfono propio.
- **Documento duplicado** en la clínica → el backend responde `409` (`patient-document-taken`); el front muestra el `detail` en un toast, pero **no** ofrece abrir la ficha que colisiona.

Los ADRs `0009`/`0010` piden cambiar estas tres cosas. (El endurecimiento de datos mínimos de `0005` queda como Open Question — ver proposal.)

## Goals / Non-Goals

**Goals:**
- Permitir teléfono compartido con **aviso no bloqueante**; exigir contacto de emergencia solo cuando falta el teléfono propio.
- Ante un documento duplicado, que el cliente pueda **llevar a la ficha existente**, no solo informar del error.

**Non-Goals:**
- No endurece los datos mínimos del alta (ADR-0005) — pendiente de decisión.
- No toca el gate clínico por etapa (ADR-0006, es de `treatment-plans`) ni consentimientos (ADR-0007, es de `treatments`).
- No cambia la unicidad del documento (sigue por clínica, `ADR-0004`/`0009` núcleo, ya construida).

## Decisions

### D1 — El aviso de teléfono compartido es no bloqueante y del lado servidor
Compartir número es **legítimo** (familias), así que **no** es un error de validación: es un **aviso**. Para poder avisar, el backend expone si el teléfono ya está en otros pacientes **de la clínica activa** (dato propio, respeta el aislamiento — no cruza clínicas). El front lo muestra como advertencia que **deja continuar**, visualmente distinta de un error de campo.
- *Alternativa descartada — bloquear el duplicado:* convierte una situación normal en un error; contradice el ADR.
- *Alternativa descartada — chequeo solo en cliente:* el cliente no conoce los demás pacientes; el dato es del servidor.

### D2 — Contacto de emergencia obligatorio **solo si** no hay teléfono propio (regla condicional)
Misma forma que la regla del responsable para menores (`superRefine` en el schema, en **paridad** con el backend, `docs/coding-style.md §7`): si `phone` está vacío, entonces `emergencyContactName` + `emergencyContactPhone` + relación pasan a obligatorios. Si hay teléfono propio, siguen opcionales. La autoridad es el backend; el cliente replica la regla para avisar antes.
- *Consecuencia sobre ADR-0005:* esto define la "excepción al celular obligatorio" que `0005` menciona. Nota: hoy el teléfono es opcional a secas; esta regla lo vuelve "obligatorio **o** contacto de emergencia", que es un endurecimiento suave y **coherente** aunque no se resuelva aún el resto de `0005`.

### D3 — El conflicto de documento identifica la ficha existente
El `409` de documento duplicado (`patient-document-taken`) SHALL incluir lo necesario para que el front **enlace** a la ficha existente (su identificador de paciente), sin filtrar más datos de los necesarios. El front, ante ese `409`, ofrece "Abrir registro existente" además de "Cancelar".
- *Aislamiento:* la ficha existente es **de la misma clínica** (la unicidad es por clínica), así que exponer su `id` a un usuario de esa clínica es correcto — no cruza tenants.
- *Anti-enumeración:* no aplica como en auth; aquí el usuario ya está autenticado y autorizado sobre su propia clínica.
- *Alternativa descartada — solo el mensaje de texto:* obliga a la recepcionista a buscar la ficha a mano; el atajo es justo lo que el ADR pide.

## Risks / Trade-offs

- **El aviso de teléfono compartido añade una consulta** en el alta/edición → aceptable (indexado por `(organizationId, phone)` si hiciera falta); es un `count`, no una carga.
- **Regla condicional de emergencia** puede confundir si la UI no explica *por qué* el campo se volvió obligatorio → mitigación: mensaje claro ("Obligatorio si el paciente no tiene teléfono propio"), como el de menores.
- **Exponer el `id` de la ficha en conflicto**: se limita a `id` (y quizá nombre para confirmar), nunca la ficha entera en el error.

## Open Questions

- Las de ADR-0005 (campos obligatorios del alta, excepción del bebé, `fullName` vs nombres+apellidos) — ver proposal. Se resuelven antes de añadir su delta; no bloquean D1–D3.
- ¿El aviso de teléfono compartido lista **cuántos**/**quiénes** lo comparten, o solo "ya está en uso por otros pacientes"? Propuesta: solo el hecho + conteo, sin nombres, para no volcar datos innecesarios en un aviso.
