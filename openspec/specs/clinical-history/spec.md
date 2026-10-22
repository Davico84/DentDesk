# clinical-history Specification

## Purpose

La Historia Clínica es el documento médico-legal inicial de la atención odontológica: reúne el motivo de consulta, la anamnesis, el examen clínico, el **diagnóstico (CIE10)** y el pronóstico de un paciente en una clínica. Existe **una por paciente, de por vida, por clínica** y es el **prerrequisito** para cualquier tratamiento — modela el criterio clínico que autoriza a tratar, hueco que ninguna otra capacidad cubre (`ADR-0011`). Esta capacidad cubre el **documento inicial** y su sellado (`DRAFT→COMPLETED`), y las **notas de consulta** posteriores con sus diagnósticos, antecedentes nuevos y corrección de error material (`ADR-0020`). El alta básica odontológica, los índices de la ficha y la corrección pedida por el paciente son fases posteriores.

## Requirements

### Requirement: Historia Clínica única por paciente y clínica

El sistema SHALL permitir crear **una sola** Historia Clínica por paciente dentro de una clínica, creada en estado `DRAFT`. Un paciente de otra clínica no SHALL ser visible ni alcanzable (aislamiento por tenant, `ADR-0004`).

#### Scenario: Crear la primera Historia Clínica del paciente
- **WHEN** un usuario autenticado crea una Historia Clínica para un paciente de su clínica que aún no tiene una
- **THEN** el sistema la crea en estado `DRAFT` y la asocia al paciente y a la clínica de sesión

#### Scenario: Rechazar una segunda Historia Clínica para el mismo paciente
- **WHEN** un usuario intenta crear una Historia Clínica para un paciente que ya tiene una (en cualquier estado) en esa clínica
- **THEN** el sistema rechaza la operación indicando que ya existe

#### Scenario: Aislamiento por clínica
- **WHEN** un usuario intenta acceder a la Historia Clínica de un paciente que pertenece a otra clínica
- **THEN** el sistema responde como si no existiera (no la expone)

#### Scenario: Creación concurrente de la primera Historia
- **WHEN** dos solicitudes intentan crear en paralelo la primera Historia Clínica del mismo paciente
- **THEN** el sistema persiste **una sola** y rechaza la otra (unicidad garantizada a nivel de datos, no solo por chequeo previo)

#### Scenario: La Historia no lee ni altera los datos clínicos del paciente
- **WHEN** se crea o edita una Historia Clínica de un paciente que ya tenía anamnesis en su ficha (`allergies`/`conditions`/`currentMedication`)
- **THEN** el sistema captura la anamnesis de la Historia de cero, sin leer, copiar ni modificar los campos clínicos de `Patient` (quedan intactos)

### Requirement: Guardado incremental en estado DRAFT

Mientras la Historia Clínica esté en `DRAFT`, el sistema SHALL permitir guardarla con secciones incompletas y editarla libremente en sesiones posteriores, sin diálogo de confirmación ni bloqueo. Guardar conserva el avance; no equivale a completar.

#### Scenario: Guardar una Historia Clínica con campos incompletos
- **WHEN** un usuario guarda una Historia Clínica en `DRAFT` con parte de las secciones vacías
- **THEN** el sistema persiste lo ingresado y la mantiene en `DRAFT`

#### Scenario: Retomar y editar un DRAFT existente
- **WHEN** un usuario reabre una Historia Clínica en `DRAFT` y modifica cualquier sección
- **THEN** el sistema guarda los cambios y la mantiene en `DRAFT`

#### Scenario: Rechazar edición directa de una Historia sellada
- **WHEN** un usuario intenta editar una sección inicial de una Historia Clínica en estado `COMPLETED`
- **THEN** el sistema rechaza la edición porque la información inicial quedó sellada

### Requirement: Diagnóstico con código CIE10 y ubicación
Cada diagnóstico SHALL referenciar un código **CIE10** existente en el catálogo oficial MINSA y SHALL llevar un **tipo de diagnóstico** obligatorio según la norma MINSA HIS: `PRESUNTIVO` (P), `DEFINITIVO` (D) o `REPETIDO` (R). El sistema SHALL registrar automáticamente y de forma **inmutable** la fecha/hora de registro (reloj del servidor) y el usuario que lo registra. Los diagnósticos SHALL poder agregarse, modificarse o eliminarse solo mientras la Historia esté en `DRAFT`.

#### Scenario: Agregar un diagnóstico general
- **WHEN** un usuario agrega, a una Historia en `DRAFT`, un diagnóstico con un código CIE10 válido, ubicación `GENERAL` y tipo (`P`/`D`/`R`)
- **THEN** el sistema lo registra y guarda automáticamente la fecha/hora y el usuario que lo registró (inmutables)

#### Scenario: Rechazar un diagnóstico sin tipo (P/D/R)
- **WHEN** un usuario intenta registrar un diagnóstico sin indicar el tipo `PRESUNTIVO`/`DEFINITIVO`/`REPETIDO`
- **THEN** el sistema rechaza la operación porque el tipo es obligatorio (norma MINSA HIS)

#### Scenario: Rechazar un código CIE10 inexistente
- **WHEN** un usuario intenta registrar un diagnóstico con un código que no está en el catálogo CIE10
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar cambios de diagnóstico en una Historia sellada
- **WHEN** un usuario intenta agregar, modificar o eliminar un diagnóstico de una Historia en estado `COMPLETED`
- **THEN** el sistema rechaza la operación en el servidor

### Requirement: Ubicación del diagnóstico por pieza FDI
Cada diagnóstico SHALL llevar una **ubicación**: `GENERAL` o `PIEZA`. Con `PIEZA`, SHALL indicar el diente por un **número FDI válido** del conjunto real de piezas (permanentes 11–18, 21–28, 31–38, 41–48; temporales 51–55, 61–65, 71–75, 81–85), no por un rango numérico laxo. Con `GENERAL`, NO SHALL aceptar número FDI.

#### Scenario: Agregar un diagnóstico ubicado en una pieza
- **WHEN** un usuario agrega, a una Historia en `DRAFT`, un diagnóstico con ubicación `PIEZA` y un número FDI válido
- **THEN** el sistema lo registra con la pieza indicada

#### Scenario: Rechazar ubicación PIEZA sin número FDI
- **WHEN** un usuario intenta registrar un diagnóstico con ubicación `PIEZA` pero sin número FDI
- **THEN** el sistema rechaza la operación indicando que la pieza es obligatoria para esa ubicación

#### Scenario: Rechazar un número FDI fuera del conjunto válido
- **WHEN** un usuario intenta registrar un diagnóstico `PIEZA` con un número que no pertenece al conjunto FDI real (p. ej. `19`, `20`, `99`)
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar FDI en un diagnóstico general
- **WHEN** un usuario intenta registrar un diagnóstico con ubicación `GENERAL` y a la vez un número FDI
- **THEN** el sistema rechaza la operación porque `GENERAL` no admite pieza

### Requirement: Búsqueda jerárquica de códigos CIE10
La búsqueda de códigos CIE10 SHALL respetar la jerarquía de 4 niveles (capítulo → grupo → categoría → subcategoría), sin aplanarla a una lista única.

#### Scenario: Buscar un código navegando la jerarquía
- **WHEN** un usuario busca un diagnóstico
- **THEN** el sistema devuelve los códigos con su ruta jerárquica (capítulo → grupo → categoría → subcategoría), no como lista plana

### Requirement: Sellado a COMPLETED con checklist mínimo afirmativo
La transición de `DRAFT` a `COMPLETED` SHALL ser manual, explícita y **unidireccional** (sin volver a `DRAFT` ni resellar), y SHALL validar un **checklist mínimo**. En alergias, medicación y antecedentes familiares un campo vacío NO SHALL contar como revisado: la ausencia SHALL registrarse de forma **afirmativa**. El checklist SHALL exigir el **odontograma inicial** del paciente y ambos documentos SHALL sellarse de forma **atómica**: si falla el odontograma, la Historia SHALL seguir en `DRAFT`.

#### Scenario: Completar cuando el checklist está satisfecho
- **WHEN** un usuario solicita marcar como `COMPLETED` una Historia Clínica cuyo checklist mínimo está cubierto y cuyo paciente tiene el odontograma inicial registrado
- **THEN** el sistema sella la Historia en estado `COMPLETED` **y** el odontograma inicial, en una sola operación

#### Scenario: Rechazar el sellado con un ítem crítico faltante
- **WHEN** un usuario intenta marcar `COMPLETED` una Historia Clínica a la que le falta un ítem del checklist mínimo
- **THEN** el sistema rechaza la transición e indica qué ítems faltan

#### Scenario: Rechazar el sellado sin odontograma inicial
- **WHEN** un usuario intenta marcar `COMPLETED` una Historia Clínica cuyo paciente no tiene odontograma inicial registrado
- **THEN** el sistema rechaza la transición e indica que falta el odontograma

#### Scenario: Un fallo al sellar el odontograma deja la Historia en DRAFT
- **WHEN** el sellado del odontograma inicial falla durante la operación de completar la Historia Clínica
- **THEN** el sistema deja la Historia en `DRAFT` y el odontograma sin sellar, sin aplicar ninguno de los dos cambios

#### Scenario: Un campo de alergias vacío no equivale a "sin hallazgos"
- **WHEN** un usuario deja el campo de alergias vacío (sin afirmar "sin alergias conocidas") e intenta completar
- **THEN** el sistema rechaza el sellado porque el ítem no fue registrado afirmativamente

#### Scenario: Un campo de medicación vacío no equivale a "no toma medicación"
- **WHEN** un usuario deja la medicación sin registrar afirmativamente (ni un detalle ni "no toma medicación") e intenta completar
- **THEN** el sistema rechaza el sellado porque el ítem no fue registrado afirmativamente

#### Scenario: Rechazar una transición inválida
- **WHEN** un usuario intenta cambiar una Historia de `COMPLETED` a `DRAFT`, o volver a ejecutar el sellado sobre una ya `COMPLETED`
- **THEN** el sistema rechaza la operación y conserva el estado `COMPLETED`

#### Scenario: Las Historias selladas antes de esta regla no se ven afectadas
- **WHEN** se consulta una Historia Clínica que fue sellada antes de que el odontograma fuera obligatorio
- **THEN** el sistema la muestra como `COMPLETED` sin exigirle un odontograma retroactivamente

### Requirement: Información inicial sellada tras COMPLETED

Una vez en `COMPLETED`, la información inicial de la Historia Clínica SHALL quedar sellada, legible en todo momento. La **API SHALL rechazar en el servidor** toda modificación, **independientemente del cliente**, aunque la petición llegue directo al endpoint. Una escritura concurrente con el sellado NO SHALL reabrir la Historia ni modificarla después de sellada. La corrección posterior de un dato queda fuera de esta capacidad (enmienda auditable, fase posterior).

#### Scenario: La Historia sellada permanece legible
- **WHEN** un usuario abre una Historia Clínica en estado `COMPLETED`
- **THEN** el sistema muestra toda su información en modo lectura

#### Scenario: Rechazar la edición de la información sellada aun por API directa
- **WHEN** se envía directamente a la API una petición para modificar el diagnóstico, los antecedentes u otra sección inicial de una Historia `COMPLETED` (sin pasar por la UI)
- **THEN** el servidor rechaza la modificación

#### Scenario: Guardar la Historia mientras otra operación la sella no la reabre
- **WHEN** un usuario guarda la información inicial de una Historia en `DRAFT` mientras otra operación la está sellando
- **THEN** el sistema rechaza el guardado porque la Historia quedó sellada
- **AND** la Historia permanece en `COMPLETED`, sin perder el sellado

#### Scenario: Los diagnósticos de una Historia que se está sellando no cambian
- **WHEN** un usuario agrega, edita o elimina un diagnóstico de una Historia en `DRAFT` mientras otra operación la está sellando
- **THEN** el sistema rechaza la operación porque la Historia quedó sellada
- **AND** la Historia sellada conserva sus diagnósticos tal como estaban al sellarse

### Requirement: Estado consultable para habilitar tratamientos

El sistema SHALL exponer si un paciente tiene, en la clínica de sesión, una Historia Clínica en estado `COMPLETED`. Solo `COMPLETED` habilita; una en `DRAFT` o la ausencia de historia NO habilita. Esta es la señal que las capacidades de tratamiento consumirán como gate.

#### Scenario: Paciente con Historia COMPLETED está habilitado
- **WHEN** se consulta el estado de habilitación de un paciente con una Historia Clínica `COMPLETED`
- **THEN** el sistema responde que está habilitado para tratamientos

#### Scenario: Paciente con solo DRAFT no está habilitado
- **WHEN** se consulta el estado de un paciente cuya Historia Clínica está en `DRAFT`
- **THEN** el sistema responde que no está habilitado

#### Scenario: Paciente sin Historia Clínica no está habilitado
- **WHEN** se consulta el estado de un paciente sin Historia Clínica
- **THEN** el sistema responde que no está habilitado

### Requirement: Filiación derivada del paciente, no duplicada

La Historia Clínica SHALL mostrar la filiación (nombre, sexo, fecha de nacimiento, dirección, teléfono) **leyéndola de `Patient`**, sin volver a capturarla ni copiarla. La filiación no forma parte de la información sellada: refleja siempre el dato vigente del paciente.

#### Scenario: La filiación se muestra sin recapturarse
- **WHEN** un usuario abre una Historia Clínica
- **THEN** el sistema muestra la filiación tomada del registro del paciente, sin pedir reingresarla

#### Scenario: La filiación refleja los datos vigentes del paciente
- **WHEN** cambia un dato de filiación del paciente y luego se abre su Historia Clínica (incluso `COMPLETED`)
- **THEN** la filiación mostrada refleja el dato actualizado del paciente

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

Cada nota SHALL llevar la **fecha y hora de la atención**, ingresada por el usuario, que NO SHALL ser futura (con un margen de un minuto para el reloj del equipo) ni anterior a la atención de la última nota sellada del paciente. Además, el sistema SHALL registrar de forma automática e inmutable **cuándo se escribió** la nota y quién la escribió.

#### Scenario: Registrar una atención de horas antes
- **WHEN** un usuario abre una nota con fecha y hora de atención de esa misma mañana
- **THEN** el sistema la registra con la hora ingresada y guarda aparte el momento real de escritura

#### Scenario: Rechazar una atención en el futuro
- **WHEN** un usuario indica una fecha y hora de atención posterior al momento actual en más de un minuto
- **THEN** el sistema rechaza el dato

#### Scenario: Rechazar una atención anterior a la última sellada
- **WHEN** un usuario indica una fecha de atención anterior a la de la última nota sellada del mismo paciente
- **THEN** el sistema rechaza el dato porque las atenciones se registran en orden cronológico

### Requirement: Nota abierta editable y descartable solo vacía

Mientras la nota esté `ABIERTA`, el sistema SHALL permitir editar sus campos, diagnósticos, antecedentes y hallazgos atendidos en varias sesiones. El sistema SHALL permitir **descartar** una nota abierta solo si no tiene contenido, diagnósticos, antecedentes ni hallazgos atendidos; con contenido, la nota NO SHALL poder descartarse.

#### Scenario: Retomar y editar una nota abierta
- **WHEN** un usuario reabre una nota `ABIERTA` y modifica un campo
- **THEN** el sistema guarda el cambio y la mantiene `ABIERTA`

#### Scenario: Descartar una nota abierta vacía
- **WHEN** un usuario descarta una nota `ABIERTA` sin contenido, sin diagnósticos, sin antecedentes y sin hallazgos atendidos
- **THEN** el sistema la elimina y el paciente puede abrir otra

#### Scenario: Rechazar el descarte de una nota con contenido
- **WHEN** un usuario intenta descartar una nota `ABIERTA` que tiene algún campo, diagnóstico, antecedente o hallazgo atendido registrado
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

### Requirement: Hallazgos del odontograma atendidos en la nota

Mientras la nota esté `ABIERTA`, el sistema SHALL permitir marcar y desmarcar hallazgos del odontograma del mismo paciente como atendidos en esa consulta, ofreciendo primero los que se ven rojos. Cada hallazgo atendido SHALL guardar una descripción tomada del hallazgo al marcarlo (pieza, cara, hallazgo y estado), su fecha y su autor, y SHALL quedar inmutable al sellarse la nota.

#### Scenario: Marcar un hallazgo pendiente como atendido
- **WHEN** un usuario marca como atendida, en una nota `ABIERTA`, una lesión de caries en la cara oclusal de la pieza 16
- **THEN** el sistema lo registra en la nota con su descripción, la fecha y el usuario
- **AND** la nota lo muestra entre sus hallazgos atendidos

#### Scenario: Los hallazgos que se ven rojos aparecen primero
- **WHEN** un usuario abre los hallazgos del odontograma desde una nota `ABIERTA`
- **THEN** el sistema muestra primero, como pendientes, los que se ven rojos (siempre rojos, o según su estado y en mal estado)
- **AND** ofrece los demás por separado, plegados

#### Scenario: Un pendiente ya atendido en una consulta anterior
- **WHEN** un hallazgo rojo fue marcado como atendido en una nota ya sellada
- **THEN** el panel indica la fecha de esa consulta
- **AND** el hallazgo se puede volver a marcar en la nota abierta

#### Scenario: Desmarcar un hallazgo mientras la nota está abierta
- **WHEN** un usuario desmarca un hallazgo atendido de una nota `ABIERTA`
- **THEN** el sistema quita el registro y la nota deja de mostrarlo

#### Scenario: Rechazar marcar dos veces el mismo hallazgo en una nota
- **WHEN** un usuario intenta marcar como atendido un hallazgo que ya está marcado en la misma nota
- **THEN** el sistema rechaza la operación y conserva el registro existente

#### Scenario: Rechazar un hallazgo de otro paciente
- **WHEN** un usuario intenta marcar en la nota un hallazgo del odontograma de otro paciente, o de otra clínica
- **THEN** el sistema rechaza la operación; el hallazgo de otra clínica se trata como inexistente

#### Scenario: Los hallazgos atendidos quedan fijos al sellar
- **WHEN** se envía directo a la API una petición para marcar o desmarcar un hallazgo en una nota `SELLADA`
- **THEN** el servidor rechaza la operación y la nota no cambia

#### Scenario: Marcar o desmarcar en paralelo con el sellado
- **WHEN** un usuario marca o desmarca un hallazgo de una nota `ABIERTA` mientras otra operación la está sellando
- **THEN** el sistema rechaza la operación porque la nota quedó sellada
- **AND** la nota sellada conserva los hallazgos atendidos que tenía al sellarse

#### Scenario: La nota sellada se lee sola
- **WHEN** un usuario lee una nota `SELLADA` con hallazgos atendidos
- **THEN** el sistema muestra cada hallazgo con la descripción guardada al marcarlo

### Requirement: Diagnóstico propuesto desde un hallazgo atendido

Desde un hallazgo atendido, el sistema SHALL proponer un diagnóstico con la ubicación del hallazgo (la pieza si es de una cara o de una pieza; general en los demás casos) y, para los hallazgos siempre rojos con sugerencia, los códigos CIE10 sugeridos. Siempre SHALL poder buscarse cualquier código del catálogo. El diagnóstico SHALL registrarse solo al confirmarlo el usuario y con las reglas de diagnóstico vigentes.

#### Scenario: Caries en dentina con su sugerencia
- **WHEN** un usuario propone el diagnóstico de una lesión de caries a nivel de la dentina en la pieza 16
- **THEN** el formulario trae la ubicación en la pieza 16 y sugiere el código K021 (caries de la dentina)
- **AND** el usuario elige el tipo P, D o R y lo confirma

#### Scenario: Hallazgo entre piezas, en un rango o en un arco
- **WHEN** un usuario propone el diagnóstico de un hallazgo registrado entre dos piezas, en un rango o en un arco
- **THEN** el formulario trae la ubicación general

#### Scenario: Hallazgo sin sugerencia
- **WHEN** un usuario propone el diagnóstico de un hallazgo que se ve rojo por estar en mal estado, como una corona
- **THEN** el formulario no sugiere códigos y ofrece el buscador del catálogo

#### Scenario: Elegir un código distinto del sugerido
- **WHEN** el formulario sugiere códigos y el usuario busca y elige otro código del catálogo
- **THEN** el sistema registra el diagnóstico con el código elegido por el usuario

#### Scenario: Nada se registra sin confirmar
- **WHEN** un usuario abre la propuesta de diagnóstico de un hallazgo y la cierra sin confirmar
- **THEN** la nota no gana ningún diagnóstico

#### Scenario: Las sugerencias existen en el catálogo
- **WHEN** el sistema sugiere códigos para cualquier tipo de hallazgo
- **THEN** cada código sugerido pertenece al catálogo CIE10 vigente
