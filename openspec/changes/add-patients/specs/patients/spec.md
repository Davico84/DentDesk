## ADDED Requirements

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

#### Scenario: Un documento no puede repetirse dentro de la misma clínica
- **WHEN** se intenta registrar en una clínica a un paciente con un documento que **ya existe en esa clínica**
- **THEN** el sistema rechaza el alta indicando que el documento ya está registrado
- **AND** no crea un segundo registro

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

### Requirement: Consulta, edición y baja del paciente
El sistema SHALL permitir consultar y editar la ficha de un paciente de la clínica activa. **Dar de baja a un paciente NO SHALL borrarlo**: su historia clínica tiene retención legal y de él cuelgan tratamientos, citas y cobros. La baja SHALL ser un **archivado**.

#### Scenario: Editar la ficha
- **WHEN** se modifican los datos de un paciente de la clínica
- **THEN** los cambios quedan guardados
- **AND** se recuperan en la siguiente consulta

#### Scenario: Dar de baja no destruye
- **WHEN** se da de baja a un paciente
- **THEN** deja de aparecer en el listado por defecto
- **AND** su ficha y su información clínica **siguen existiendo** y pueden recuperarse

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

#### Scenario: Una consulta que omite el filtro sigue estando aislada
- **WHEN** se ejecuta una consulta de pacientes **sin filtrar explícitamente por clínica**
- **THEN** el resultado sigue restringido a la clínica de la petición
- **AND** el aislamiento no depende de que el filtro se haya escrito
