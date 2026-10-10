# patients Specification

## Purpose

La ficha clínico-administrativa de la persona atendida en una clínica. Es el **núcleo del dominio**: de ella cuelgan el odontograma, los tratamientos, las citas, las imágenes y los cobros.

El paciente es un **cliente de la clínica**, no un usuario del sistema: no tiene cuenta ni inicia sesión — lo registra el personal (administrador, recepcionista u odontólogo). Y **pertenece a una sola clínica**: la misma persona atendida en dos clínicas son dos fichas distintas, y ninguna sabe de la otra.

Cubre el alta, la consulta, la edición, la baja (que **archiva**, nunca borra) y el **listado con búsqueda** —el uso diario de recepción—, con las reglas que el dominio exige: **el menor de edad requiere responsable**, y la anamnesis (alergias, antecedentes, medicación) se registra de forma que **pueda alertarse** antes de un tratamiento.

## Requirements

### Requirement: Alta del paciente en la clínica
El sistema SHALL permitir al personal de una clínica registrar a una persona atendida, con sus datos personales, de contacto y de emergencia. El paciente **no es un usuario del sistema**: no tiene cuenta ni inicia sesión. El paciente SHALL pertenecer a la clínica que lo registra.

#### Scenario: Alta con los datos mínimos
- **WHEN** un miembro autenticado de la clínica registra a una persona con su nombre y su fecha de nacimiento
- **THEN** el paciente queda registrado en **esa** clínica
- **AND** puede recuperarse después

#### Scenario: No se puede dar de alta sin los datos mínimos
- **WHEN** se intenta registrar a una persona sin nombre o sin fecha de nacimiento
- **THEN** el sistema rechaza el alta indicando **qué campo** falta
- **AND** no registra nada

#### Scenario: El documento es opcional
- **WHEN** se registra a un paciente **sin documento de identidad** (un bebé, por ejemplo)
- **THEN** el alta se completa igualmente
- **AND** la clínica no queda impedida de atenderlo

#### Scenario: El documento va emparejado con su tipo
- **WHEN** se registra a un paciente con un **número de documento pero sin indicar el tipo** (o al revés)
- **THEN** el sistema rechaza el alta indicando qué falta (el tipo, o el número)
- **AND** no registra nada

#### Scenario: El documento tiene el formato del tipo elegido
- **WHEN** se registra a un paciente con un documento cuyo **formato no corresponde a su tipo** (p. ej. un DNI que no son 8 dígitos)
- **THEN** el sistema rechaza el alta indicando que el documento no es válido para ese tipo
- **AND** no registra nada

#### Scenario: Un documento no puede repetirse dentro de la misma clínica
- **WHEN** se intenta registrar en una clínica a un paciente con un documento que **ya existe en esa clínica**
- **THEN** el sistema rechaza el alta indicando que el documento ya está registrado
- **AND** no crea un segundo registro

#### Scenario: El documento de un paciente dado de baja sigue ocupado
- **WHEN** se intenta registrar a una persona con el documento de un paciente **dado de baja** de la misma clínica
- **THEN** el sistema rechaza el alta indicando que el documento ya está registrado
- **AND** no crea un segundo registro para esa persona (su ficha existente puede reactivarse)

#### Scenario: El mismo documento SÍ puede existir en otra clínica
- **WHEN** una clínica registra a un paciente con un documento que **ya existe en otra clínica** de la plataforma
- **THEN** el alta se completa con normalidad
- **AND** ninguna de las dos clínicas puede saber que la otra atiende a esa persona

### Requirement: Un paciente menor de edad exige un responsable
El sistema SHALL exigir los datos del **responsable/tutor** cuando el paciente sea **menor de edad**. La minoría de edad SHALL **derivarse de la fecha de nacimiento**, y NO SHALL declararse como un dato aparte (un dato declarado se desincroniza el día del cumpleaños; uno derivado, nunca).

#### Scenario: Menor sin responsable
- **WHEN** se intenta registrar a un paciente cuya fecha de nacimiento indica que es **menor de edad**, sin los datos del responsable
- **THEN** el sistema rechaza el alta indicando que el responsable es obligatorio para menores
- **AND** no registra nada

#### Scenario: Menor con responsable
- **WHEN** se registra a un paciente menor de edad **con** los datos de su responsable (nombre, parentesco y teléfono)
- **THEN** el alta se completa

#### Scenario: Un adulto no necesita responsable
- **WHEN** se registra a un paciente cuya fecha de nacimiento indica que es **mayor de edad**, sin datos de responsable
- **THEN** el alta se completa

#### Scenario: La condición de menor se recalcula sola
- **WHEN** se consulta un paciente
- **THEN** su condición de menor o adulto se corresponde con su **fecha de nacimiento en el momento de la consulta**, sin que nadie haya tenido que actualizarla

### Requirement: La anamnesis se registra de forma que pueda alertarse
El sistema SHALL registrar los antecedentes clínicos del paciente —**alergias**, **condiciones/patologías** y **medicación actual**— como **listas de valores diferenciables**, no como un texto libre. Deben poder recorrerse para **avisar antes de un tratamiento**: un texto libre no permite alertar de forma fiable, y aquí un fallo tiene consecuencias sobre una persona.

#### Scenario: Se registran los antecedentes
- **WHEN** se registra a un paciente con varias alergias, varias condiciones y varios medicamentos
- **THEN** se guardan como elementos **individuales** y se recuperan como tales
- **AND** cada uno puede consultarse por separado

#### Scenario: La anamnesis puede estar vacía
- **WHEN** se registra a un paciente sin antecedentes conocidos
- **THEN** el alta se completa
- **AND** su anamnesis queda vacía, no ausente

### Requirement: Búsqueda y listado de pacientes
El sistema SHALL permitir buscar y listar los pacientes **de la clínica activa**, con paginación. Es el uso diario de recepción, así que SHALL poder buscarse por **nombre** y por **documento**.

#### Scenario: Buscar por nombre
- **WHEN** se busca por una parte del nombre de un paciente
- **THEN** aparece en los resultados
- **AND** no aparecen pacientes de otras clínicas

#### Scenario: Buscar por documento
- **WHEN** se busca por el documento de un paciente
- **THEN** aparece en los resultados

#### Scenario: El listado se pagina
- **WHEN** la clínica tiene más pacientes de los que caben en una página
- **THEN** el resultado indica cuántos hay en total
- **AND** permite recorrer las páginas siguientes

#### Scenario: Los pacientes dados de baja no aparecen por defecto
- **WHEN** se lista la clínica sin pedir nada especial
- **THEN** solo aparecen los pacientes **activos**

#### Scenario: Se pueden listar los pacientes dados de baja
- **WHEN** se pide **explícitamente** incluir a los dados de baja
- **THEN** también aparecen los pacientes inactivos de la clínica
- **AND** se distinguen de los activos

### Requirement: Consulta, edición y baja del paciente
El sistema SHALL permitir consultar y editar la ficha de un paciente de la clínica activa. **Dar de baja a un paciente NO SHALL borrarlo**: su historia clínica tiene retención legal y de él cuelgan tratamientos, citas y cobros. La baja SHALL ser un **archivado**.

#### Scenario: Editar la ficha
- **WHEN** se modifican los datos de un paciente de la clínica
- **THEN** los cambios quedan guardados
- **AND** se recuperan en la siguiente consulta

#### Scenario: No se puede editar el documento a uno que ya existe en la clínica
- **WHEN** se edita un paciente para ponerle un documento que **ya tiene otro paciente de la misma clínica** (activo o dado de baja)
- **THEN** el sistema rechaza el cambio indicando que el documento ya está registrado
- **AND** no modifica nada

#### Scenario: Dar de baja no destruye
- **WHEN** se da de baja a un paciente
- **THEN** deja de aparecer en el listado por defecto
- **AND** su ficha y su información clínica **siguen existiendo** y pueden recuperarse

#### Scenario: Reactivar a un paciente dado de baja
- **WHEN** se reactiva a un paciente que estaba dado de baja
- **THEN** vuelve a aparecer en el listado por defecto
- **AND** conserva toda su información y su historia clínica

#### Scenario: Consultar un paciente inexistente
- **WHEN** se consulta un paciente que no existe en la clínica activa
- **THEN** el sistema responde que no se ha encontrado

### Requirement: Los pacientes de una clínica son invisibles para otra
El sistema SHALL garantizar que una clínica **no puede leer ni modificar** los pacientes de otra, **bajo ninguna circunstancia**. Es la promesa central del SaaS aplicada al dato más sensible del producto: una fuga aquí es una fuga de **historia clínica**.

#### Scenario: Una consulta solo alcanza a los pacientes propios
- **WHEN** un miembro de la clínica A lista o busca pacientes
- **THEN** solo obtiene pacientes de la clínica A
- **AND** ninguno de la clínica B, aunque compartan la instalación

#### Scenario: No se puede acceder a un paciente de otra clínica ni conociendo su identificador
- **WHEN** un miembro de la clínica A intenta consultar, editar o dar de baja a un paciente de la clínica B **indicando su identificador exacto**
- **THEN** el sistema responde que no se ha encontrado
- **AND** no revela que ese paciente existe en otra clínica

### Requirement: Autoría de la ficha del paciente
El sistema SHALL registrar, para cada ficha de paciente, qué usuario la creó y qué usuario realizó
su última modificación, usando el mecanismo automático de autoría de `multi-tenancy`. Los pacientes
creados antes de que este mecanismo existiera SHALL conservar autoría desconocida en vez de un valor
inventado.

#### Scenario: Se registra la autoría al crear y editar
- **WHEN** un miembro de la clínica registra un paciente nuevo o edita uno existente
- **THEN** el sistema registra su identidad como autor de esa operación
- **AND** una edición posterior por otro miembro actualiza solo el autor de la última modificación

#### Scenario: Los pacientes anteriores a este mecanismo no tienen autoría inventada
- **WHEN** se consulta un paciente que existía antes de que el sistema registrara autoría
- **THEN** su autoría de creación y de última modificación **quedan sin asignar**
- **AND** el sistema no le atribuye un autor que no le corresponde

#### Scenario: Una consulta que omite el filtro sigue estando aislada
- **WHEN** se ejecuta una consulta de pacientes **sin filtrar explícitamente por clínica**
- **THEN** el resultado sigue restringido a la clínica de la petición
- **AND** el aislamiento no depende de que el filtro se haya escrito

### Requirement: Contacto telefónico del paciente
El sistema SHALL permitir que **varios pacientes de la misma clínica compartan el mismo número** de teléfono, sin bloquear el registro; si el número ya está asociado a otros pacientes de la **clínica activa** (sin cruzar organizaciones), SHALL mostrar un **aviso no bloqueante**. El sistema SHALL exigir un **contacto de emergencia** (nombre, teléfono y relación) **solo** cuando el paciente **no** registre un teléfono propio; con teléfono propio, SHALL seguir siendo opcional.

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
Cuando el alta o la edición de un paciente choca con un documento que **ya existe en la clínica activa**, el sistema SHALL rechazar el duplicado y además SHALL **identificar la ficha existente** para que el cliente pueda ofrecer abrirla. La información expuesta SHALL limitarse a lo necesario para enlazar a esa ficha. Esto SHALL respetar el aislamiento: la ficha en conflicto pertenece **siempre** a la clínica activa (la unicidad del documento es por clínica).

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
