## Purpose

La Historia Clínica es el documento médico-legal inicial de la atención odontológica: reúne el motivo de consulta, la anamnesis, el examen clínico, el **diagnóstico (CIE10)** y el pronóstico de un paciente en una clínica. Existe **una por paciente, de por vida, por clínica** y es el **prerrequisito** para cualquier tratamiento — modela el criterio clínico que autoriza a tratar, hueco que ninguna otra capacidad cubre (`ADR-0011`). Esta capacidad cubre el **documento inicial** y su sellado (`DRAFT→COMPLETED`); la evolución/alta y las enmiendas son fases posteriores.

## ADDED Requirements

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

Cada diagnóstico de la Historia Clínica SHALL referenciar un código **CIE10** existente en el catálogo oficial MINSA y SHALL llevar una **ubicación**: `GENERAL`, `REGION` o `PIEZA`. Cuando la ubicación es `PIEZA`, SHALL indicar el diente por su **número FDI**. La búsqueda de códigos SHALL respetar la jerarquía de 4 niveles (capítulo → grupo → categoría → subcategoría), sin aplanarla a una lista única.

#### Scenario: Agregar un diagnóstico general
- **WHEN** un usuario agrega un diagnóstico con un código CIE10 válido y ubicación `GENERAL`
- **THEN** el sistema lo registra en la Historia Clínica

#### Scenario: Agregar un diagnóstico ubicado en una pieza
- **WHEN** un usuario agrega un diagnóstico con ubicación `PIEZA` y un número FDI válido
- **THEN** el sistema lo registra con la pieza indicada

#### Scenario: Rechazar un código CIE10 inexistente
- **WHEN** un usuario intenta registrar un diagnóstico con un código que no está en el catálogo CIE10
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar ubicación PIEZA sin número FDI
- **WHEN** un usuario intenta registrar un diagnóstico con ubicación `PIEZA` pero sin número FDI
- **THEN** el sistema rechaza la operación indicando que la pieza es obligatoria para esa ubicación

#### Scenario: Buscar un código navegando la jerarquía
- **WHEN** un usuario busca un diagnóstico
- **THEN** el sistema devuelve los códigos con su ruta jerárquica (capítulo → grupo → categoría → subcategoría), no como lista plana

### Requirement: Sellado a COMPLETED con checklist mínimo afirmativo

La transición de `DRAFT` a `COMPLETED` SHALL ser una acción manual y explícita que valida un **checklist mínimo** de completitud clínica antes de sellar. Un campo vacío NO SHALL contar como revisado: la ausencia de alergias o de medicación debe registrarse de forma **afirmativa** ("sin alergias conocidas" / "no toma medicación"), distinta de "no llenado".

#### Scenario: Completar cuando el checklist está satisfecho
- **WHEN** un usuario solicita marcar como `COMPLETED` una Historia Clínica cuyo checklist mínimo está cubierto
- **THEN** el sistema la sella en estado `COMPLETED`

#### Scenario: Rechazar el sellado con un ítem crítico faltante
- **WHEN** un usuario intenta marcar `COMPLETED` una Historia Clínica a la que le falta un ítem del checklist mínimo
- **THEN** el sistema rechaza la transición e indica qué ítems faltan

#### Scenario: Un campo vacío no equivale a "sin hallazgos"
- **WHEN** un usuario deja el campo de alergias vacío (sin afirmar "sin alergias conocidas") e intenta completar
- **THEN** el sistema rechaza el sellado porque el ítem no fue registrado afirmativamente

### Requirement: Información inicial sellada tras COMPLETED

Una vez en `COMPLETED`, la información inicial de la Historia Clínica SHALL quedar sellada: no se edita directamente. Permanece legible en todo momento. La corrección posterior de un dato queda fuera de esta capacidad (enmienda auditable, fase posterior).

#### Scenario: La Historia sellada permanece legible
- **WHEN** un usuario abre una Historia Clínica en estado `COMPLETED`
- **THEN** el sistema muestra toda su información en modo lectura

#### Scenario: Rechazar la edición de la información sellada
- **WHEN** un usuario intenta modificar el diagnóstico, los antecedentes u otra sección inicial de una Historia `COMPLETED`
- **THEN** el sistema rechaza la modificación

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
