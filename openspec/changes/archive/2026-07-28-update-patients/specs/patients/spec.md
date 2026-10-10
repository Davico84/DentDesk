## ADDED Requirements

### Requirement: Contacto telefónico del paciente
El sistema SHALL permitir que **varios pacientes de la misma clínica compartan el mismo número** de teléfono, sin bloquear el registro; cuando el número ya esté asociado a otros pacientes de la clínica, SHALL mostrar un **aviso no bloqueante** que deja continuar. El sistema SHALL exigir un **contacto de emergencia** (nombre, teléfono y relación) **únicamente** cuando el paciente **no** registre un teléfono propio; si el paciente tiene teléfono propio, el contacto de emergencia SHALL seguir siendo opcional. La detección de número compartido SHALL acotarse a la **clínica activa** (no cruza organizaciones).

#### Scenario: Dos pacientes comparten el mismo teléfono
- **WHEN** se registra a un paciente con un teléfono que **ya está asociado a otro paciente de la misma clínica**
- **THEN** el sistema completa el alta igualmente
- **AND** muestra un aviso indicando que el número ya está en uso por otros pacientes
- **AND** el aviso no impide guardar

#### Scenario: Paciente sin teléfono propio exige contacto de emergencia
- **WHEN** se intenta registrar a un paciente **sin teléfono propio** y **sin** contacto de emergencia
- **THEN** el sistema rechaza el alta indicando que se requiere un contacto de emergencia
- **AND** no registra nada

#### Scenario: Paciente sin teléfono propio pero con contacto de emergencia
- **WHEN** se registra a un paciente **sin teléfono propio** pero **con** un contacto de emergencia completo (nombre, teléfono y relación)
- **THEN** el alta se completa con normalidad

#### Scenario: Paciente con teléfono propio no necesita contacto de emergencia
- **WHEN** se registra a un paciente **con** teléfono propio y **sin** contacto de emergencia
- **THEN** el alta se completa con normalidad

#### Scenario: El número compartido no se detecta entre clínicas
- **WHEN** se registra en una clínica a un paciente con un teléfono que existe en **otra** clínica de la plataforma
- **THEN** el alta se completa sin ningún aviso de número compartido
- **AND** ninguna clínica puede inferir los pacientes de la otra

#### Scenario: Editar un paciente no lo cuenta a sí mismo como número compartido
- **WHEN** se edita un paciente que ya comparte su teléfono con otro paciente de la clínica, sin cambiar el teléfono
- **THEN** el sistema no cuenta al propio paciente al evaluar el número compartido
- **AND** el aviso solo refleja a los **otros** pacientes que de verdad comparten ese número

#### Scenario: Quitar el teléfono propio al editar exige contacto de emergencia
- **WHEN** se edita un paciente para quitarle su teléfono propio y no tiene un contacto de emergencia registrado
- **THEN** el sistema rechaza la edición indicando que se requiere un contacto de emergencia
- **AND** no aplica el cambio

### Requirement: Ofrecer la ficha existente ante un documento duplicado
Cuando el alta o la edición de un paciente choca con un documento que **ya existe en la clínica activa**, el sistema SHALL rechazar el duplicado (como ya hace) y además SHALL **identificar la ficha existente** para que el cliente pueda ofrecer abrirla. La información expuesta SHALL limitarse a lo necesario para enlazar a esa ficha, sin volcar datos innecesarios. Esto SHALL respetar el aislamiento: la ficha en conflicto pertenece **siempre** a la clínica activa (la unicidad del documento es por clínica).

#### Scenario: El conflicto de documento identifica la ficha existente
- **WHEN** se intenta registrar a un paciente con un documento que ya tiene otro paciente de la clínica activa
- **THEN** el sistema rechaza el alta indicando que el documento ya está registrado
- **AND** la respuesta identifica la ficha existente de modo que el cliente pueda ofrecer abrirla

#### Scenario: El usuario abre la ficha existente desde el aviso de conflicto
- **WHEN** el sistema informa de un documento duplicado y ofrece abrir la ficha existente
- **AND** el usuario elige abrirla
- **THEN** el cliente navega a la ficha del paciente ya registrado con ese documento

#### Scenario: El conflicto de documento no cruza clínicas
- **WHEN** el documento existe en **otra** clínica pero no en la activa
- **THEN** no hay conflicto y el alta se completa con normalidad
- **AND** la respuesta no revela nada de la ficha de la otra clínica
