## Purpose

El **odontograma** es el registro gráfico del **estado** dental de un paciente, pieza a pieza y por superficie, en notación FDI. Es un **anexo obligatorio de la historia clínica** por norma peruana: sin él, la Historia Clínica está incompleta como documento médico-legal.

Esta capacidad cubre el **estado** de la boca — qué hay en cada pieza y desde cuándo. **No** cubre las **acciones** que lo modifican (procedimientos, coste, consentimiento): eso es `treatments`, que depende de esta capacidad y no al revés. Una obturación es un **estado** de la pieza; obturarla es una **acción**.

El odontograma se modela como un **bloque fechado** que se sella: lo registrado queda como evidencia de cómo se encontró la boca en esa fecha. Esta primera versión construye el **odontograma inicial**, sellado junto con la admisión; la evolución (bloques diarios de control, cierre de hallazgos y comparación entre fechas) llega en `update-dental-chart` (`ADR-0012`).

## ADDED Requirements

### Requirement: Odontograma inicial único por paciente y clínica

El sistema SHALL permitir crear **un solo** odontograma inicial por paciente dentro de una clínica, creado en estado abierto (editable). La unicidad SHALL estar garantizada a nivel de datos, no solo por comprobación previa, de modo que dos solicitudes concurrentes no puedan crear dos. Un paciente de otra clínica no SHALL ser visible ni alcanzable.

#### Scenario: Crear el odontograma inicial de un paciente
- **WHEN** un usuario autenticado crea el odontograma inicial de un paciente de su clínica que aún no tiene uno
- **THEN** el sistema lo crea en estado abierto, asociado al paciente y a la clínica de sesión

#### Scenario: Rechazar un segundo odontograma inicial
- **WHEN** un usuario intenta crear un odontograma inicial para un paciente que ya tiene uno
- **THEN** el sistema rechaza la operación indicando que ya existe

#### Scenario: Creación concurrente del odontograma inicial
- **WHEN** dos solicitudes intentan crear en paralelo el odontograma inicial del mismo paciente
- **THEN** el sistema persiste **uno solo** y rechaza el otro

#### Scenario: Aislamiento por clínica
- **WHEN** un usuario intenta acceder al odontograma de un paciente que pertenece a otra clínica
- **THEN** el sistema responde como si no existiera

### Requirement: Registro de hallazgos con localización precisa

Cada hallazgo del odontograma SHALL indicar **qué** condición es y **dónde** está. La localización SHALL tomar una de tres formas: la **superficie** de una pieza, la **pieza completa**, o el **espacio entre dos piezas** (necesario para condiciones que no pertenecen a ninguna pieza, como el diastema o el diente supernumerario). La forma de localización SHALL corresponder al alcance del tipo de condición: un tipo de superficie NO SHALL aceptar una localización de pieza completa, ni a la inversa. Las piezas SHALL pertenecer al conjunto real de la notación FDI, y la superficie SHALL ser una de las aplicables a esa pieza. Mientras el odontograma esté abierto, los hallazgos SHALL poder corregirse o eliminarse.

#### Scenario: Registrar un hallazgo en la superficie de una pieza
- **WHEN** un usuario registra una condición de alcance de superficie sobre una pieza válida y una de sus superficies aplicables
- **THEN** el sistema la registra en esa superficie

#### Scenario: Registrar un hallazgo de pieza completa
- **WHEN** un usuario registra una condición de alcance de pieza (por ejemplo, ausencia) sobre una pieza válida
- **THEN** el sistema la registra sobre la pieza, sin superficie

#### Scenario: Registrar un hallazgo entre dos piezas
- **WHEN** un usuario registra una condición que ocurre entre dos piezas adyacentes (diastema, supernumerario)
- **THEN** el sistema la registra sobre ese espacio, sin atribuirla a ninguna de las dos piezas

#### Scenario: Rechazar una pieza inexistente
- **WHEN** un usuario intenta registrar un hallazgo sobre un número que no corresponde a ninguna pieza real (por ejemplo `19`, `29`, `99`)
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar una superficie no aplicable a la pieza
- **WHEN** un usuario intenta registrar un hallazgo en una superficie que esa pieza no tiene (por ejemplo, una superficie oclusal en un incisivo)
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar una localización incoherente con el alcance del tipo
- **WHEN** un usuario intenta registrar una condición de pieza completa indicando una superficie, o una condición de superficie sin indicarla
- **THEN** el sistema rechaza la operación señalando el campo incorrecto

#### Scenario: Rechazar hallazgos de superficie en una pieza ausente
- **WHEN** un usuario intenta registrar una condición sobre la superficie de una pieza ya marcada como ausente
- **THEN** el sistema rechaza la operación por contradicción

#### Scenario: El mismo espacio entre piezas no se duplica por el orden
- **WHEN** un usuario registra un hallazgo entre las piezas `11` y `21`, y luego intenta registrar el mismo tipo entre `21` y `11`
- **THEN** el sistema lo reconoce como la misma localización y rechaza el duplicado

#### Scenario: Corregir un hallazgo mientras el odontograma está abierto
- **WHEN** un usuario elimina o corrige un hallazgo de un odontograma que aún no fue sellado
- **THEN** el sistema aplica el cambio

### Requirement: Catálogo cerrado de condiciones, alineado a la norma vigente

Los tipos de condición registrables SHALL formar un **conjunto cerrado**, alineado al catálogo y a las **siglas oficiales** de la norma vigente del odontograma (`ADR-0013`). Cada tipo SHALL declarar su **comportamiento clínico**, que determina su representación visual sin almacenarse como color:

- **patológico** — siempre requiere intervención;
- **descriptivo** — nunca implica patología activa;
- **restauración** — depende del **estado** del propio hallazgo (en buen estado o en mal estado).

Los hallazgos de tipo restauración SHALL registrar su estado, y el sistema SHALL rechazarlo en los tipos que no lo admiten. Los tipos que lo requieran SHALL llevar además un **grado** (la movilidad dental) y SHALL **obligar a registrar la entrada correspondiente en las especificaciones** (la movilidad, su clasificación; la prótesis, su material).

#### Scenario: Rechazar un tipo de condición fuera del catálogo
- **WHEN** un usuario intenta registrar un hallazgo con un tipo de condición que no pertenece al catálogo
- **THEN** el sistema rechaza la operación

#### Scenario: Registrar una restauración en buen estado
- **WHEN** un usuario registra un sellante indicando que está en buen estado
- **THEN** el sistema lo registra como restauración sin patología activa

#### Scenario: Registrar la misma restauración en mal estado
- **WHEN** un usuario registra un sellante indicando que está en mal estado
- **THEN** el sistema lo registra como una condición que requiere intervención, pese a ser el mismo tipo

#### Scenario: Rechazar una restauración sin indicar su estado
- **WHEN** un usuario intenta registrar una prótesis sin indicar si está en buen o mal estado
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar un estado en una condición que no lo admite
- **WHEN** un usuario intenta registrar una caries indicando un estado de conservación
- **THEN** el sistema rechaza la operación

#### Scenario: Una restauración y una patología coexisten en la misma pieza
- **WHEN** un usuario registra un sellante y, en la misma pieza, una lesión de caries adyacente
- **THEN** el sistema registra ambos hallazgos, sin que uno reemplace al otro

#### Scenario: Registrar una condición que exige grado
- **WHEN** un usuario registra una movilidad dental indicando su grado
- **THEN** el sistema la registra con ese grado

#### Scenario: Rechazar una condición que exige grado sin indicarlo
- **WHEN** un usuario intenta registrar una movilidad dental sin indicar el grado
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar un grado en una condición que no lo admite
- **WHEN** un usuario intenta registrar una caries indicando un grado
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar el sellado si un tipo que exige especificación no la tiene
- **WHEN** se intenta sellar un odontograma con una movilidad registrada pero sin la clasificación anotada en especificaciones
- **THEN** el sistema rechaza el sellado indicando qué falta

#### Scenario: Avisar cuando la pieza no corresponde a la edad del paciente
- **WHEN** un usuario registra un hallazgo sobre una pieza temporal en un paciente adulto (o permanente en un lactante)
- **THEN** el sistema **registra el hallazgo** y devuelve un aviso de incoherencia con la edad, sin bloquear la operación

### Requirement: Especificaciones del odontograma

El odontograma SHALL incluir una sección de **especificaciones u observaciones** en texto libre, donde se describe lo que se mide pero no se representa gráficamente (por ejemplo, la recesión gingival). Esa sección SHALL estar registrada antes de poder sellar el odontograma; registrar que no hay observaciones es una afirmación válida, dejarla sin revisar no lo es.

#### Scenario: Registrar las especificaciones
- **WHEN** un usuario registra las especificaciones de un odontograma abierto
- **THEN** el sistema las guarda

#### Scenario: Rechazar el sellado sin especificaciones revisadas
- **WHEN** se intenta sellar un odontograma cuyas especificaciones nunca fueron registradas
- **THEN** el sistema rechaza el sellado indicando que faltan

### Requirement: Odontograma sellado e inmutable

Una vez sellado, el odontograma SHALL quedar en solo lectura de forma **unidireccional**: no vuelve a estado abierto ni se vuelve a sellar. La **API SHALL rechazar en el servidor** cualquier operación que intente modificar sus hallazgos o sus especificaciones, **independientemente del cliente** — aunque la petición se envíe directamente al endpoint, sin pasar por la interfaz. Permanece legible en todo momento. Un odontograma sin hallazgos SHALL poder sellarse: una boca sana es un registro clínico válido.

#### Scenario: El odontograma sellado permanece legible
- **WHEN** un usuario abre un odontograma ya sellado
- **THEN** el sistema muestra todos sus hallazgos y especificaciones en modo lectura

#### Scenario: Rechazar la modificación de un odontograma sellado aun por API directa
- **WHEN** se envía directamente a la API una petición para agregar, corregir o eliminar un hallazgo de un odontograma sellado
- **THEN** el servidor rechaza la modificación

#### Scenario: Rechazar una transición inválida
- **WHEN** se intenta devolver un odontograma sellado a estado abierto, o volver a sellarlo
- **THEN** el sistema rechaza la operación y conserva el estado sellado

#### Scenario: Sellar un odontograma sin hallazgos
- **WHEN** se sella el odontograma de un paciente sin ninguna patología ni restauración, con sus especificaciones registradas
- **THEN** el sistema lo sella, porque la ausencia de hallazgos es un registro clínico válido
