# clinical-history Specification

## Purpose

La Historia Clínica es el documento médico-legal inicial de la atención odontológica: reúne el motivo de consulta, la anamnesis, el examen clínico, el **diagnóstico (CIE10)** y el pronóstico de un paciente en una clínica. Existe **una por paciente, de por vida, por clínica** y es el **prerrequisito** para cualquier tratamiento — modela el criterio clínico que autoriza a tratar, hueco que ninguna otra capacidad cubre (`ADR-0011`). Esta capacidad cubre el **documento inicial** y su sellado (`DRAFT→COMPLETED`); la evolución/alta y las enmiendas son fases posteriores.

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
