## Purpose

El **odontograma** es el registro gráfico del **estado** dental de un paciente, pieza a pieza y por superficie, en notación FDI. Es un **anexo obligatorio de la historia clínica** por norma peruana: sin él, la Historia Clínica está incompleta como documento médico-legal.

Esta capacidad cubre el **estado** de la boca — qué hay en cada pieza y desde cuándo. **No** cubre las **acciones** que lo modifican (procedimientos, coste, consentimiento): eso es `treatments`, que depende de esta capacidad y no al revés. Una obturación es un **estado** de la pieza; obturarla es una **acción**.

El odontograma se modela como un **documento fechado** que se sella: lo registrado queda como evidencia de cómo se encontró la boca en esa fecha. La norma genera uno nuevo **por evento clínico** —al iniciar y al culminar el plan de tratamiento, ante hallazgos nuevos, al reingresar el paciente y a pedido judicial— no uno por día. Esta primera versión construye el **odontograma inicial**, sellado junto con la admisión; la evolución (odontogramas posteriores, cierre de hallazgos y comparación entre fechas) llega en `update-dental-chart`.

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
Cada hallazgo SHALL indicar **qué** condición es y **dónde** está, con una de **cinco** localizaciones: la **superficie** de una pieza, la **pieza completa**, el **espacio entre dos piezas** (diastema, supernumerario), un **rango de piezas** (prótesis parcial, ortodoncia fija: **un solo** hallazgo) o un **arco completo** (edentulismo total, prótesis completa). La localización SHALL corresponder al alcance del tipo: un tipo de superficie NO SHALL aceptar una pieza completa, ni a la inversa.

#### Scenario: Registrar un hallazgo en la superficie de una pieza
- **WHEN** un usuario registra una condición de alcance de superficie sobre una pieza válida y una de sus superficies aplicables
- **THEN** el sistema la registra en esa superficie

#### Scenario: Registrar un hallazgo de pieza completa
- **WHEN** un usuario registra una condición de alcance de pieza (por ejemplo, ausencia) sobre una pieza válida
- **THEN** el sistema la registra sobre la pieza, sin superficie

#### Scenario: Registrar un hallazgo entre dos piezas
- **WHEN** un usuario registra una condición que ocurre entre dos piezas adyacentes (diastema, supernumerario)
- **THEN** el sistema la registra sobre ese espacio, sin atribuirla a ninguna de las dos piezas

#### Scenario: Registrar un hallazgo sobre un rango de piezas
- **WHEN** un usuario registra una prótesis parcial fija que compromete varias piezas
- **THEN** el sistema la registra como **un solo** hallazgo sobre el conjunto de piezas, conservando el orden

#### Scenario: Registrar un hallazgo sobre un arco completo
- **WHEN** un usuario registra una prótesis completa superior
- **THEN** el sistema la registra como **un solo** hallazgo sobre el arco superior, sin expandirla en un hallazgo por pieza

#### Scenario: Rechazar una localización incoherente con el alcance del tipo
- **WHEN** un usuario intenta registrar una condición de pieza completa indicando una superficie, o una condición de superficie sin indicarla
- **THEN** el sistema rechaza la operación señalando el campo incorrecto

#### Scenario: Rechazar un rango con una sola pieza
- **WHEN** un usuario intenta registrar una condición de alcance de rango indicando una única pieza
- **THEN** el sistema rechaza la operación, porque un rango comprende varias piezas

#### Scenario: El mismo espacio entre piezas no se duplica por el orden
- **WHEN** un usuario registra un hallazgo entre las piezas `11` y `21`, y luego intenta registrar el mismo tipo entre `21` y `11`
- **THEN** el sistema lo reconoce como la misma localización y rechaza el duplicado

### Requirement: Piezas y superficies válidas según FDI
Las piezas de un hallazgo SHALL pertenecer al conjunto real de la notación FDI, y la superficie SHALL ser una de las aplicables a esa pieza.

#### Scenario: Rechazar una pieza inexistente
- **WHEN** un usuario intenta registrar un hallazgo sobre un número que no corresponde a ninguna pieza real (por ejemplo `19`, `29`, `99`)
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar una superficie no aplicable a la pieza
- **WHEN** un usuario intenta registrar un hallazgo en una superficie que esa pieza no tiene (por ejemplo, una superficie oclusal en un incisivo)
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar hallazgos de superficie en una pieza ausente
- **WHEN** un usuario intenta registrar una condición sobre la superficie de una pieza ya marcada como ausente
- **THEN** el sistema rechaza la operación por contradicción

### Requirement: Corrección de hallazgos con el odontograma abierto
Mientras el odontograma esté abierto, los hallazgos SHALL poder corregirse o eliminarse.

#### Scenario: Corregir un hallazgo mientras el odontograma está abierto
- **WHEN** un usuario elimina o corrige un hallazgo de un odontograma que aún no fue sellado
- **THEN** el sistema aplica el cambio

### Requirement: Catálogo cerrado de condiciones, alineado a la norma vigente
Los tipos de condición registrables SHALL formar un **conjunto cerrado**, alineado al catálogo de la norma vigente del odontograma. Un tipo SHALL poder tener **subtipos** (variantes por material, profundidad o causa con el mismo alcance y comportamiento), y el sistema SHALL exigir el subtipo en los tipos que lo tienen y rechazarlo en los que no. Cada tipo y subtipo SHALL llevar su **sigla oficial** como dato normativo, que el sistema SHALL exponer sin alterar.

#### Scenario: Rechazar un tipo de condición fuera del catálogo
- **WHEN** un usuario intenta registrar un hallazgo con un tipo de condición que no pertenece al catálogo
- **THEN** el sistema rechaza la operación

#### Scenario: Registrar un hallazgo con su subtipo
- **WHEN** un usuario registra una lesión de caries indicando que llega a dentina
- **THEN** el sistema la registra con ese subtipo y expone su sigla oficial

#### Scenario: Rechazar un tipo con subtipos sin indicarlo
- **WHEN** un usuario intenta registrar una lesión de caries sin precisar su subtipo
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar un subtipo que no pertenece al tipo
- **WHEN** un usuario intenta registrar una corona indicando un subtipo de otro tipo de condición
- **THEN** el sistema rechaza la operación, aunque ese subtipo exista en el catálogo

#### Scenario: Avisar cuando la pieza no corresponde a la edad del paciente
- **WHEN** un usuario registra un hallazgo sobre una pieza temporal en un paciente adulto (o permanente en un lactante)
- **THEN** el sistema **registra el hallazgo** y devuelve un aviso de incoherencia con la edad, sin bloquear la operación

### Requirement: Color del hallazgo según el comportamiento de su tipo
Cada tipo SHALL declarar **uno** de tres comportamientos de color, no almacenados como color: **siempre azul** (característica sin estado), **siempre rojo** (patología o tratamiento temporal) o **según el estado** (restauraciones y aparatos, en buen o mal estado). Los hallazgos *según el estado* SHALL registrarlo, y el sistema SHALL rechazarlo en los demás. El color SHALL depender **únicamente** del propio hallazgo —su tipo y, si aplica, su estado—, nunca de otros hallazgos de la misma pieza.

#### Scenario: Registrar una restauración en buen estado
- **WHEN** un usuario registra un sellante indicando que está en buen estado
- **THEN** el sistema lo registra como un hallazgo que no requiere intervención

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

#### Scenario: Un hallazgo sin estado convive con una patología sin cambiar por ella
- **WHEN** un usuario registra una giroversión y, en la misma pieza, una lesión de caries
- **THEN** el sistema registra ambos y **conserva el comportamiento propio de cada uno**: la caries requiere intervención y la giroversión no

#### Scenario: Un implante convive con la ausencia de la pieza
- **WHEN** un usuario registra un implante en una pieza ya marcada como ausente
- **THEN** el sistema registra ambos hallazgos, porque un implante no implica ni reemplaza la ausencia

### Requirement: Grado y especificación obligatorios según el tipo
Los tipos que lo requieran SHALL llevar además un **grado** (la movilidad dental) y SHALL **obligar a registrar la entrada correspondiente en las especificaciones**.

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

### Requirement: Integridad del hallazgo y de su localización

El sistema SHALL rechazar todo hallazgo cuya localización sea incoherente con su alcance o internamente contradictoria, **y la garantía SHALL vivir en la base de datos** para las cuatro variantes de cardinalidad fija.

#### Scenario: Rechazar un rango de una sola pieza
- **WHEN** se registra un hallazgo de alcance rango con una única pieza
- **THEN** el sistema lo rechaza, porque un rango de una pieza es un hallazgo de pieza mal localizado

#### Scenario: Rechazar un rango sin piezas
- **WHEN** se registra un hallazgo de alcance rango sin ninguna pieza
- **THEN** el sistema lo rechaza

#### Scenario: Rechazar una pieza repetida dentro de un rango
- **WHEN** se registra un rango que incluye dos veces la misma pieza
- **THEN** el sistema lo rechaza

#### Scenario: Rechazar piezas de rango en un hallazgo que no es de rango
- **WHEN** se intenta asociar piezas de rango a un hallazgo cuyo alcance no es rango
- **THEN** el sistema lo rechaza

#### Scenario: Rechazar un arco que no sea superior ni inferior
- **WHEN** se registra un hallazgo de arco con un valor distinto de superior o inferior
- **THEN** el sistema lo rechaza

#### Scenario: Rechazar entre-piezas con la misma pieza dos veces
- **WHEN** se registra un hallazgo entre piezas indicando dos veces la misma pieza
- **THEN** el sistema lo rechaza

#### Scenario: Normalizar el orden en un hallazgo entre piezas
- **WHEN** se registra un hallazgo entre piezas indicándolas en orden inverso
- **THEN** el sistema lo persiste en orden canónico, de modo que no puedan existir dos registros del mismo hallazgo

#### Scenario: Rechazar un hallazgo duplicado emitido de forma concurrente
- **WHEN** dos solicitudes simultáneas registran el mismo tipo y subtipo en la misma localización del mismo odontograma
- **THEN** una prevalece y la otra es rechazada, sin que queden dos hallazgos iguales

#### Scenario: Rechazar un hallazgo que apunta a un odontograma de otra organización
- **WHEN** se intenta registrar un hallazgo cuya organización no coincide con la del odontograma referenciado
- **THEN** el sistema lo rechaza

#### Scenario: Aislar las piezas de un rango entre organizaciones
- **WHEN** una sesión de una organización consulta o escribe las piezas de rango de otra organización
- **THEN** no obtiene ninguna fila y no puede escribir

### Requirement: El sellado del odontograma es irreversible y protege sus hallazgos

Un odontograma sellado SHALL rechazar toda modificación, **incluidas las de sus hallazgos y de las piezas de sus rangos**, y el estado del sellado SHALL ser coherente por construcción.

#### Scenario: Rechazar la reapertura de un odontograma sellado
- **WHEN** se intenta devolver a abierto un odontograma sellado
- **THEN** el sistema lo rechaza

#### Scenario: Rechazar el segundo sellado
- **WHEN** se intenta sellar un odontograma ya sellado
- **THEN** el sistema lo rechaza y la fecha de sellado original no cambia

#### Scenario: Rechazar la modificación de un hallazgo sellado
- **WHEN** se intenta modificar o eliminar un hallazgo de un odontograma sellado, aun por API directa
- **THEN** el sistema lo rechaza

#### Scenario: Rechazar la modificación de las piezas de un rango sellado
- **WHEN** se intenta modificar las piezas de rango de un hallazgo de un odontograma sellado
- **THEN** el sistema lo rechaza

#### Scenario: Rechazar un hallazgo agregado durante un sellado concurrente
- **WHEN** se registra un hallazgo mientras otra operación sella el mismo odontograma
- **THEN** una de las dos prevalece y el odontograma sellado no queda con un hallazgo agregado después de su fecha de sellado

#### Scenario: Sellar la admisión bloquea en un orden único
- **WHEN** dos operaciones sellan concurrentemente la admisión del mismo paciente
- **THEN** una prevalece y la otra es rechazada, sin bloqueo mutuo entre ambas

### Requirement: Especificaciones y observaciones del odontograma
El odontograma SHALL incluir **dos** secciones de texto distintas que no se sustituyen: **especificaciones**, el detalle de hallazgos del catálogo que no cabe en el gráfico (clasificación de una movilidad, material de una corona temporal, color del metal de una corona), y **observaciones**, los hallazgos clínicos que la nomenclatura no contempla. Ambas SHALL estar registradas antes de sellar el odontograma; registrar que no hay nada que anotar es válido, dejarlas sin revisar no.

#### Scenario: Registrar las especificaciones
- **WHEN** un usuario registra las especificaciones de un odontograma abierto
- **THEN** el sistema las guarda

#### Scenario: Registrar las observaciones
- **WHEN** un usuario registra un hallazgo que la nomenclatura no contempla en las observaciones
- **THEN** el sistema las guarda, separadas de las especificaciones

#### Scenario: Rechazar el sellado sin especificaciones revisadas
- **WHEN** se intenta sellar un odontograma cuyas especificaciones nunca fueron registradas
- **THEN** el sistema rechaza el sellado indicando que faltan

#### Scenario: Distinguir una sección revisada y vacía de una nunca revisada
- **WHEN** un usuario registra que no hay especificaciones que anotar
- **THEN** el sistema guarda esa afirmación como un dato distinto de la sección nunca revisada, y el sellado deja de estar bloqueado por ella

#### Scenario: Corregir el texto no vuelve a exigir revisión
- **WHEN** un usuario edita el texto de una sección ya revisada de un odontograma abierto
- **THEN** la sección sigue revisada, porque quien escribe está revisando

#### Scenario: Rechazar el sellado sin observaciones revisadas
- **WHEN** se intenta sellar un odontograma con las especificaciones registradas pero cuyas observaciones nunca fueron revisadas
- **THEN** el sistema rechaza el sellado, porque una sección no cubre a la otra

### Requirement: Odontograma sellado e inmutable
Una vez sellado, el odontograma SHALL quedar en solo lectura de forma **unidireccional**: no vuelve a abrirse ni se vuelve a sellar, y permanece legible. La **API SHALL rechazar en el servidor** toda modificación de sus hallazgos, especificaciones u observaciones, **independientemente del cliente**, aunque la petición llegue directo al endpoint. Un odontograma sin hallazgos SHALL poder sellarse: una boca sana es un registro clínico válido.

#### Scenario: El odontograma sellado permanece legible
- **WHEN** un usuario abre un odontograma ya sellado
- **THEN** el sistema muestra todos sus hallazgos, especificaciones y observaciones en modo lectura

#### Scenario: Rechazar la modificación de un odontograma sellado aun por API directa
- **WHEN** se envía directamente a la API una petición para agregar, corregir o eliminar un hallazgo de un odontograma sellado
- **THEN** el servidor rechaza la modificación

#### Scenario: Rechazar una transición inválida
- **WHEN** se intenta devolver un odontograma sellado a estado abierto, o volver a sellarlo
- **THEN** el sistema rechaza la operación y conserva el estado sellado

#### Scenario: Sellar un odontograma sin hallazgos
- **WHEN** se sella el odontograma de un paciente sin ninguna patología ni restauración, con sus especificaciones y observaciones registradas
- **THEN** el sistema lo sella, porque la ausencia de hallazgos es un registro clínico válido
