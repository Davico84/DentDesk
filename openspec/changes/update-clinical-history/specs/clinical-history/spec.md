# Spec Delta — clinical-history

## ADDED Requirements

### Requirement: Nota de consulta posterior a la admisión

El sistema SHALL permitir registrar una **nota de consulta** por cada atención posterior a la admisión, solo cuando el paciente está activo y tiene su Historia Clínica en `COMPLETED`. Un paciente SHALL tener como máximo **una nota abierta** a la vez. Las notas de un paciente de otra clínica no SHALL ser visibles ni alcanzables.

#### Scenario: Abrir una nota de consulta
- **WHEN** un usuario abre una nota de consulta para un paciente de su clínica con la Historia Clínica `COMPLETED` y sin otra nota abierta
- **THEN** el sistema crea la nota en estado `ABIERTA`, asociada al paciente y a la clínica de sesión

#### Scenario: Rechazar la nota si la Historia no está sellada
- **WHEN** un usuario intenta abrir una nota de consulta para un paciente cuya Historia Clínica está en `DRAFT` o que no tiene Historia
- **THEN** el sistema rechaza la operación indicando que primero debe completarse la Historia Clínica

#### Scenario: Rechazar la nota de un paciente dado de baja
- **WHEN** un usuario intenta abrir una nota de consulta para un paciente dado de baja (archivado)
- **THEN** el sistema rechaza la operación indicando que primero debe reactivarse al paciente

#### Scenario: Rechazar una segunda nota abierta
- **WHEN** un usuario intenta abrir una nota de consulta para un paciente que ya tiene una nota `ABIERTA`
- **THEN** el sistema rechaza la operación indicando que hay una nota sin sellar

#### Scenario: Apertura concurrente de notas
- **WHEN** dos solicitudes intentan abrir en paralelo una nota de consulta para el mismo paciente sin notas abiertas
- **THEN** el sistema crea **una sola** nota y rechaza la otra

#### Scenario: Aislamiento por clínica de las notas
- **WHEN** un usuario intenta leer o modificar una nota de consulta de un paciente de otra clínica
- **THEN** el sistema responde como si no existiera

### Requirement: Contenido mínimo de la nota de consulta

La nota SHALL registrar el contenido de las consultas siguientes de NTS 139 §5.2.1: **obligatorios** para sellar el motivo de consulta, el examen (evaluación odontológica), el tratamiento y al menos un diagnóstico; **opcionales** el tiempo de enfermedad, los síntomas y signos, el tratamiento recibido con su cumplimiento y resultado, las funciones biológicas, los exámenes auxiliares, la referencia y la fecha de próxima cita.

#### Scenario: Guardar una nota con solo parte del contenido
- **WHEN** un usuario guarda una nota `ABIERTA` con el motivo de consulta y sin los demás campos
- **THEN** el sistema persiste lo ingresado y la mantiene `ABIERTA`

#### Scenario: La próxima cita es solo una fecha posterior a la atención
- **WHEN** un usuario registra como próxima cita una fecha igual o anterior a la fecha de la atención
- **THEN** el sistema rechaza el dato indicando que la próxima cita debe ser posterior a la atención

#### Scenario: Leer una nota con su contenido completo
- **WHEN** un usuario abre una nota de consulta, abierta o sellada
- **THEN** el sistema muestra todos sus campos, sus diagnósticos y sus antecedentes registrados

### Requirement: Fecha y hora de la atención

Cada nota SHALL llevar la **fecha y hora de la atención**, ingresada por el usuario, que NO SHALL ser futura ni anterior a la atención de la última nota sellada del paciente. Además, el sistema SHALL registrar de forma automática e inmutable **cuándo se escribió** la nota y quién la escribió.

#### Scenario: Registrar una atención de horas antes
- **WHEN** un usuario abre una nota con fecha y hora de atención de esa misma mañana
- **THEN** el sistema la registra con la hora ingresada y guarda aparte el momento real de escritura

#### Scenario: Rechazar una atención en el futuro
- **WHEN** un usuario indica una fecha y hora de atención posterior al momento actual
- **THEN** el sistema rechaza el dato

#### Scenario: Rechazar una atención anterior a la última sellada
- **WHEN** un usuario indica una fecha de atención anterior a la de la última nota sellada del mismo paciente
- **THEN** el sistema rechaza el dato porque las atenciones se registran en orden cronológico

### Requirement: Nota abierta editable y descartable solo vacía

Mientras la nota esté `ABIERTA`, el sistema SHALL permitir editar sus campos, diagnósticos y antecedentes en varias sesiones. El sistema SHALL permitir **descartar** una nota abierta solo si no tiene contenido, diagnósticos ni antecedentes; con contenido, la nota NO SHALL poder descartarse.

#### Scenario: Retomar y editar una nota abierta
- **WHEN** un usuario reabre una nota `ABIERTA` y modifica un campo
- **THEN** el sistema guarda el cambio y la mantiene `ABIERTA`

#### Scenario: Descartar una nota abierta vacía
- **WHEN** un usuario descarta una nota `ABIERTA` sin contenido, sin diagnósticos y sin antecedentes
- **THEN** el sistema la elimina y el paciente puede abrir otra

#### Scenario: Rechazar el descarte de una nota con contenido
- **WHEN** un usuario intenta descartar una nota `ABIERTA` que tiene algún campo, diagnóstico o antecedente registrado
- **THEN** el sistema rechaza la operación y conserva la nota

### Requirement: Sellado irreversible de la nota

El sellado SHALL ser explícito y **unidireccional** (`ABIERTA → SELLADA`), y SHALL exigir el contenido obligatorio. Una nota sellada SHALL ser inmutable: el servidor SHALL rechazar toda modificación o descarte, aun por API directa, y una escritura concurrente con el sellado NO SHALL alterarla.

#### Scenario: Sellar una nota completa
- **WHEN** un usuario sella una nota `ABIERTA` con motivo, examen, tratamiento y al menos un diagnóstico
- **THEN** el sistema la pasa a `SELLADA` y registra cuándo y quién la selló

#### Scenario: Rechazar el sellado con contenido obligatorio faltante
- **WHEN** un usuario intenta sellar una nota sin examen o sin ningún diagnóstico
- **THEN** el sistema rechaza el sellado e indica qué falta

#### Scenario: Rechazar cambios a una nota sellada aun por API directa
- **WHEN** se envía directo a la API una petición para editar un campo, agregar o quitar un diagnóstico o antecedente, o descartar una nota `SELLADA`
- **THEN** el servidor rechaza la operación y la nota no cambia

#### Scenario: Rechazar un segundo sellado
- **WHEN** un usuario intenta sellar una nota que ya está `SELLADA`
- **THEN** el sistema rechaza la operación y conserva el sellado original

#### Scenario: Una escritura concurrente no altera la nota que se sella
- **WHEN** un usuario guarda un campo o un diagnóstico de una nota `ABIERTA` mientras otra operación la está sellando
- **THEN** el sistema rechaza la escritura porque la nota quedó sellada
- **AND** la nota sellada conserva el contenido que tenía al sellarse

### Requirement: Diagnósticos de la nota de consulta

Cada diagnóstico de la nota SHALL seguir las mismas reglas que los de la Historia: código **CIE10** del catálogo, tipo **P/D/R** obligatorio (NTS 139 §4.2.9), ubicación `GENERAL` o `PIEZA` con FDI válido, y fecha/hora y usuario de registro inmutables. Solo SHALL agregarse, modificarse o eliminarse mientras la nota esté `ABIERTA`.

#### Scenario: Agregar un diagnóstico a la nota
- **WHEN** un usuario agrega a una nota `ABIERTA` un diagnóstico con código CIE10 válido, ubicación y tipo `P` o `D`
- **THEN** el sistema lo registra con la fecha/hora y el usuario que lo registró

#### Scenario: Rechazar un diagnóstico de nota inválido
- **WHEN** un usuario intenta registrar en una nota un diagnóstico sin tipo, con un código fuera del catálogo o con una pieza FDI inexistente
- **THEN** el sistema rechaza la operación

### Requirement: Diagnóstico repetitivo exige un registro previo

El sistema SHALL aceptar un diagnóstico de tipo **R (repetitivo)** en una nota solo si el mismo código CIE10 ya fue registrado antes al paciente, en su Historia Clínica o en una nota sellada. En otro caso SHALL rechazarlo.

#### Scenario: Registrar un diagnóstico repetitivo ya diagnosticado en la Historia
- **WHEN** un usuario agrega a una nota un diagnóstico tipo `R` con un código CIE10 que figura en la Historia Clínica del paciente
- **THEN** el sistema lo registra

#### Scenario: Registrar un diagnóstico repetitivo ya diagnosticado en una nota anterior
- **WHEN** un usuario agrega un diagnóstico tipo `R` con un código registrado en una nota sellada anterior del mismo paciente
- **THEN** el sistema lo registra

#### Scenario: Rechazar un repetitivo sin diagnóstico previo
- **WHEN** un usuario agrega un diagnóstico tipo `R` con un código que nunca se registró al paciente, o que solo figura en la misma nota abierta
- **THEN** el sistema rechaza la operación indicando que un diagnóstico repetitivo requiere uno anterior

### Requirement: Corrección de un error material como registro nuevo

Quien escribió una nota sellada SHALL poder corregir un error material de ella (DS 009-2017-SA art. 47 c) con un **registro nuevo** que indique el campo, el texto corregido y el motivo, sin modificar el original. La corrección NO SHALL aplicar a diagnósticos ni a campos que estaban vacíos (omisión), y SHALL ser inmutable.

#### Scenario: Corregir un error material de una nota sellada
- **WHEN** el autor de una nota `SELLADA` registra una corrección sobre el campo de examen con el texto corregido y el motivo
- **THEN** el sistema guarda la corrección con su fecha, hora y autor
- **AND** el texto original de la nota sigue intacto y se muestra junto a la corrección

#### Scenario: Rechazar la corrección de otro usuario
- **WHEN** un usuario distinto del que escribió la nota intenta registrar una corrección sobre ella
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar la corrección de un diagnóstico
- **WHEN** el autor intenta registrar una corrección sobre un diagnóstico de la nota
- **THEN** el sistema rechaza la operación porque la corrección de error material no aplica a diagnósticos

#### Scenario: Rechazar la corrección de una omisión
- **WHEN** el autor intenta corregir un campo que en la nota sellada estaba vacío
- **THEN** el sistema rechaza la operación porque la omisión no se corrige como error material

#### Scenario: Rechazar la corrección sin motivo o de una nota abierta
- **WHEN** se intenta registrar una corrección sin motivo, o sobre una nota que sigue `ABIERTA`
- **THEN** el sistema rechaza la operación

### Requirement: Antecedentes nuevos como asientos de alta y baja

Dentro de una nota abierta, el sistema SHALL permitir registrar antecedentes nuevos en tres categorías: **alergias y reacciones adversas**, **antecedentes patológicos personales** y **medicación actual**. Cada asiento SHALL ser un **alta** o la **baja** de un alta anterior vigente del mismo paciente. Al sellarse la nota, sus asientos SHALL quedar inmutables: la baja no borra el alta original.

#### Scenario: Registrar una alergia nueva en la consulta
- **WHEN** un usuario registra en una nota `ABIERTA` un alta de alergia con su descripción
- **THEN** el sistema guarda el asiento ligado a la nota, con la fecha de atención y el usuario

#### Scenario: Dar de baja una medicación que el paciente dejó
- **WHEN** un usuario registra en una nota `ABIERTA` la baja de un alta de medicación vigente del paciente
- **THEN** el sistema guarda la baja como asiento nuevo
- **AND** el alta original sigue existiendo sin cambios

#### Scenario: Rechazar la baja de un asiento que no está vigente
- **WHEN** un usuario intenta dar de baja un alta que ya fue dada de baja, o que pertenece a otro paciente
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar un asiento sin categoría válida o sin descripción
- **WHEN** un usuario intenta registrar un alta con una categoría fuera de las tres o sin descripción
- **THEN** el sistema rechaza la operación

### Requirement: Antecedentes vigentes del paciente

El sistema SHALL mostrar los **antecedentes vigentes** del paciente por categoría, reuniendo tres fuentes distinguibles: lo registrado en la Historia Clínica con su fecha, las altas de notas selladas sin baja, y la anamnesis de la ficha del paciente marcada como **registrada al dar de alta, sin autoría clínica**.

#### Scenario: Ver antecedentes de las tres fuentes
- **WHEN** un usuario consulta los antecedentes vigentes de un paciente con alergias en su ficha, en su Historia y en una nota sellada
- **THEN** el sistema muestra las tres, cada una con su origen y su fecha

#### Scenario: Una baja saca el antecedente de la vista vigente
- **WHEN** una nota sellada dio de baja una medicación registrada en una nota anterior
- **THEN** esa medicación no figura entre los vigentes
- **AND** sigue visible en el historial de la nota donde se registró

#### Scenario: Los asientos de una nota abierta no son vigentes todavía
- **WHEN** se consultan los antecedentes vigentes mientras hay una nota `ABIERTA` con un alta nueva
- **THEN** el alta no figura entre los vigentes hasta que la nota se selle
