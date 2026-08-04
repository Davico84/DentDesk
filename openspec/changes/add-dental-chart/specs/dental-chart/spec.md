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

Cada hallazgo del odontograma SHALL indicar **qué** condición es y **dónde** está. La localización SHALL tomar una de **cinco** formas: la **superficie** de una pieza, la **pieza completa**, el **espacio entre dos piezas** (para condiciones que no pertenecen a ninguna pieza, como el diastema o el supernumerario), un **rango de piezas** (una prótesis parcial o un aparato de ortodoncia fijo comprometen varias piezas y son **un solo** hallazgo, no uno por pieza) y un **arco completo** (superior o inferior, para el edentulismo total y la prótesis completa). La forma de localización SHALL corresponder al alcance del tipo de condición: un tipo de superficie NO SHALL aceptar una localización de pieza completa, ni a la inversa. Las piezas SHALL pertenecer al conjunto real de la notación FDI, y la superficie SHALL ser una de las aplicables a esa pieza. Mientras el odontograma esté abierto, los hallazgos SHALL poder corregirse o eliminarse.

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

#### Scenario: Rechazar una pieza inexistente
- **WHEN** un usuario intenta registrar un hallazgo sobre un número que no corresponde a ninguna pieza real (por ejemplo `19`, `29`, `99`)
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar una superficie no aplicable a la pieza
- **WHEN** un usuario intenta registrar un hallazgo en una superficie que esa pieza no tiene (por ejemplo, una superficie oclusal en un incisivo)
- **THEN** el sistema rechaza la operación

#### Scenario: Rechazar una localización incoherente con el alcance del tipo
- **WHEN** un usuario intenta registrar una condición de pieza completa indicando una superficie, o una condición de superficie sin indicarla
- **THEN** el sistema rechaza la operación señalando el campo incorrecto

#### Scenario: Rechazar un rango con una sola pieza
- **WHEN** un usuario intenta registrar una condición de alcance de rango indicando una única pieza
- **THEN** el sistema rechaza la operación, porque un rango comprende varias piezas

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

Los tipos de condición registrables SHALL formar un **conjunto cerrado**, alineado al catálogo de la norma vigente del odontograma. Un tipo SHALL poder tener **subtipos** —variantes por material, profundidad o causa que comparten alcance y comportamiento— y el sistema SHALL exigir el subtipo en los tipos que lo tienen y rechazarlo en los que no. Cada tipo y subtipo SHALL llevar su **sigla oficial** como dato normativo, que el sistema SHALL exponer sin alterar.

Cada tipo SHALL declarar **uno** de tres comportamientos de color, que determinan su representación sin almacenarse como color:

- **siempre azul** — característica sin estado que evaluar (macrodoncia, diastema, giroversión, pieza ausente);
- **siempre rojo** — patología **o tratamiento temporal** (caries, fractura, movilidad, corona temporal);
- **según el estado del hallazgo** — restauraciones y aparatos, que pueden estar en buen o mal estado (corona definitiva, sellante, implante, prótesis, ortodoncia).

Los hallazgos de comportamiento *según el estado* SHALL registrarlo, y el sistema SHALL rechazarlo en los demás. Los tipos que lo requieran SHALL llevar además un **grado** (la movilidad dental) y SHALL **obligar a registrar la entrada correspondiente en las especificaciones**.

El color de un hallazgo SHALL depender **únicamente de sí mismo** —su tipo y, si aplica, su estado—, nunca de otros hallazgos de la misma pieza.

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

### Requirement: Especificaciones y observaciones del odontograma

El odontograma SHALL incluir **dos** secciones de texto distintas, con propósitos que no se sustituyen:

- **Especificaciones** — el detalle de hallazgos que **sí** están en el catálogo pero no cabe representado gráficamente: la clasificación de una movilidad, el material de una corona temporal, el color del metal de una corona.
- **Observaciones** — los hallazgos clínicos que la nomenclatura del odontograma **no contempla**. Es la vía prevista para lo que no tiene sigla oficial.

Ambas SHALL estar registradas antes de poder sellar el odontograma; registrar que no hay nada que anotar es una afirmación válida, dejarlas sin revisar no lo es.

#### Scenario: Registrar las especificaciones
- **WHEN** un usuario registra las especificaciones de un odontograma abierto
- **THEN** el sistema las guarda

#### Scenario: Registrar las observaciones
- **WHEN** un usuario registra un hallazgo que la nomenclatura no contempla en las observaciones
- **THEN** el sistema las guarda, separadas de las especificaciones

#### Scenario: Rechazar el sellado sin especificaciones revisadas
- **WHEN** se intenta sellar un odontograma cuyas especificaciones nunca fueron registradas
- **THEN** el sistema rechaza el sellado indicando que faltan

#### Scenario: Rechazar el sellado sin observaciones revisadas
- **WHEN** se intenta sellar un odontograma con las especificaciones registradas pero cuyas observaciones nunca fueron revisadas
- **THEN** el sistema rechaza el sellado, porque una sección no cubre a la otra

### Requirement: Odontograma sellado e inmutable

Una vez sellado, el odontograma SHALL quedar en solo lectura de forma **unidireccional**: no vuelve a estado abierto ni se vuelve a sellar. La **API SHALL rechazar en el servidor** cualquier operación que intente modificar sus hallazgos, sus especificaciones o sus observaciones, **independientemente del cliente** — aunque la petición se envíe directamente al endpoint, sin pasar por la interfaz. Permanece legible en todo momento. Un odontograma sin hallazgos SHALL poder sellarse: una boca sana es un registro clínico válido.

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
