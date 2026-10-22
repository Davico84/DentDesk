# dental-chart Specification

## Purpose

El **odontograma** es el registro gráfico del **estado** dental de un paciente, pieza a pieza y por superficie, en notación FDI. Es un **anexo obligatorio de la historia clínica** por norma peruana: sin él, la Historia Clínica está incompleta como documento médico-legal.

Esta capacidad cubre el **estado** de la boca — qué hay en cada pieza y desde cuándo. **No** cubre las **acciones** que lo modifican (procedimientos, coste, consentimiento): eso es `treatments`, que depende de esta capacidad y no al revés. Una obturación es un **estado** de la pieza; obturarla es una **acción**.

El odontograma se modela como un **documento fechado** que se sella: lo registrado queda como evidencia de cómo se encontró la boca en esa fecha. La norma genera uno nuevo **por evento clínico** —al iniciar y al culminar el plan de tratamiento, ante hallazgos nuevos, al reingresar el paciente y a pedido judicial— no uno por día. Esta primera versión construye el **odontograma inicial**, sellado junto con la admisión; la evolución (odontogramas posteriores, cierre de hallazgos y comparación entre fechas) llega en `update-dental-chart`.

## Requirements

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
Los tipos que lo requieran SHALL llevar además un **grado** (la movilidad dental). Los tipos que la norma obliga a detallar (corona, corona temporal, fluorosis) SHALL **exigir texto en las especificaciones** antes de sellar: con uno de ellos registrado, dejarlas revisadas y vacías no alcanza.

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
- **WHEN** se intenta sellar un odontograma con una corona registrada y las especificaciones vacías (sin el color del metal)
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
El odontograma SHALL incluir **dos** secciones de texto distintas que no se sustituyen: **especificaciones**, el detalle de hallazgos del catálogo que no cabe en el gráfico (color del metal de una corona, material de una corona temporal, clasificación de una fluorosis), y **observaciones**, los hallazgos clínicos que la nomenclatura no contempla. Ambas SHALL estar registradas antes de sellar el odontograma; registrar que no hay nada que anotar es válido, dejarlas sin revisar no.

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

### Requirement: Odontograma legible en pantallas angostas
El dibujo del odontograma SHALL conservar un ancho mínimo legible (el que hoy tiene en tablet) y, si la pantalla es más angosta, SHALL desplazarse de costado dentro de su propia tarjeta, sin que la página se desplace de costado. Las etiquetas de cada fila (permanente, temporal) SHALL quedar alineadas con su fila a cualquier ancho.

#### Scenario: Odontograma en celular
- **WHEN** se abre el odontograma de un paciente a 375 px de ancho
- **THEN** el dibujo (etiquetas y piezas) mide al menos 600 px de ancho y se desplaza de costado dentro de su tarjeta
- **AND** la página no se desplaza de costado

#### Scenario: Hay más dibujo a los lados
- **WHEN** el odontograma no entra completo en el ancho disponible
- **THEN** el sistema indica que se puede deslizar para ver el resto

#### Scenario: Etiquetas alineadas con sus filas
- **WHEN** el odontograma se muestra a 375, 768 o 1280 px
- **THEN** cada etiqueta de fila queda a la altura de su fila de piezas, sin franjas vacías entre ellas

#### Scenario: En escritorio no cambia
- **WHEN** el ancho disponible alcanza para el dibujo completo
- **THEN** el odontograma se ve entero, sin desplazamiento ni indicación de deslizar

### Requirement: Lista de hallazgos legible en celular
En pantallas de menos de 640 px, la lista de hallazgos del odontograma SHALL mostrar cada hallazgo completo (sigla, tipo, localización y estado) sin columnas ocultas ni desplazamiento lateral. Desde 640 px SHALL mostrarse como tabla.

#### Scenario: Hallazgos en celular
- **WHEN** se abre a 375 px un odontograma con hallazgos
- **THEN** cada hallazgo muestra a la vista su sigla, su tipo, su localización y su estado

#### Scenario: Eliminar un hallazgo en celular
- **WHEN** el odontograma está abierto y se ve a 375 px
- **THEN** cada hallazgo conserva su acción de eliminar, con su nombre accesible

#### Scenario: Hallazgos en tablet y escritorio
- **WHEN** se abre a 768 px o más un odontograma con hallazgos
- **THEN** los hallazgos se muestran en la tabla de cuatro columnas

### Requirement: Guía para registrar hallazgos sobre el odontograma

Con el odontograma abierto, el sistema SHALL ofrecer una guía paso a paso que resalte, en el propio gráfico del
paciente, dónde se toca para cada localización (superficie, pieza completa, entre piezas, arco y rango) y que nombre
ejemplos de hallazgos de cada una tomados del catálogo vigente. SHALL abrirse sola la primera vez que el usuario ve un
odontograma editable y quedar disponible desde un botón. SHALL NOT registrar ni modificar nada.

#### Scenario: La primera vez se abre sola
- **WHEN** un usuario abre por primera vez un odontograma que puede editar
- **THEN** la guía se abre sola en su primer paso

#### Scenario: Una vez vista no vuelve a abrirse sola
- **WHEN** el usuario ya completó o cerró la guía y vuelve a abrir un odontograma editable
- **THEN** la guía no se abre sola
- **AND** puede abrirla desde el botón "¿Cómo registro hallazgos?"

#### Scenario: Cada paso resalta dónde tocar
- **WHEN** el usuario avanza al paso de pieza completa
- **THEN** la guía resalta el número de una pieza en el gráfico
- **AND** explica que ese lugar es para hallazgos como los que el catálogo define con ese alcance, por ejemplo corona o
  tratamiento de conducto

#### Scenario: Se recorre en ambos sentidos y se cierra en cualquier momento
- **WHEN** el usuario retrocede, avanza o cierra la guía, con el mouse o con el teclado
- **THEN** la guía va al paso pedido o se cierra
- **AND** al cerrarla el odontograma queda exactamente como estaba, sin hallazgos nuevos

#### Scenario: Tocar la zona resaltada no registra nada
- **WHEN** durante la guía el usuario hace click sobre la pieza o la cara resaltada
- **THEN** no se abre el registro de hallazgos y el odontograma no cambia

#### Scenario: El paso actual se anuncia
- **WHEN** un usuario de lector de pantalla avanza de paso
- **THEN** se anuncia el número de paso, el total y la explicación del paso

#### Scenario: En pantallas angostas el resaltado queda a la vista
- **WHEN** la zona que resalta un paso está fuera de la vista porque el odontograma se desliza dentro de su tarjeta
- **THEN** la guía la trae a la vista antes de resaltarla

#### Scenario: Durante el modo ubicar no hay guía
- **WHEN** el usuario está ubicando un hallazgo en el odontograma
- **THEN** la guía no se abre sola y su botón no está disponible hasta salir de ese modo

#### Scenario: Con el odontograma sellado no hay guía
- **WHEN** el usuario abre un odontograma sellado
- **THEN** la guía no se abre sola y el botón no se ofrece

### Requirement: Búsqueda de hallazgos por nombre y modo ubicar

El tipo de hallazgo SHALL elegirse en un selector con búsqueda sin tildes sobre todo el catálogo, que lista primero los
tipos de la zona tocada. Si el elegido va en otra zona, SHALL ofrecer ir a la ubicación deducible de lo tocado o, si no
se deduce, elegirla en el odontograma (modo ubicar), donde solo responden las zonas válidas. SHALL poder buscarse desde
la barra del odontograma. Nada SHALL cambiar sin confirmación. En edición solo SHALL listar tipos de la zona actual.

#### Scenario: El tipo buscado va en la zona tocada
- **WHEN** el usuario tocó una cara y escribe "caries" en el tipo de hallazgo
- **THEN** caries aparece entre los tipos de esa zona y, al elegirla, queda elegida para esa cara

#### Scenario: Elegir el tipo con el teclado
- **WHEN** el usuario escribe en el tipo de hallazgo, se mueve con las flechas y confirma con Enter
- **THEN** queda elegido el tipo resaltado
- **AND** Escape con la lista abierta cierra solo la lista, sin cerrar el registro

#### Scenario: Desde una cara, un hallazgo de pieza completa
- **WHEN** el usuario tocó una cara de la pieza 16 y elige "corona", que figura en otra zona
- **THEN** el diálogo indica que la corona se registra sobre la pieza completa y ofrece registrarla en la pieza 16
- **AND** al aceptarlo abre el registro de la pieza 16 con corona elegida, directamente en el formulario aunque la
  pieza 16 ya tenga otros hallazgos

#### Scenario: Desde una pieza, un hallazgo de arco
- **WHEN** el usuario tocó la pieza 36 y elige un hallazgo que se registra sobre un arco completo
- **THEN** el diálogo ofrece registrarlo en el arco inferior, y al aceptarlo abre ese registro con el hallazgo elegido

#### Scenario: Desde una pieza, un hallazgo de rango
- **WHEN** el usuario tocó la pieza 14 y elige un hallazgo que se registra sobre un rango de piezas
- **THEN** el diálogo ofrece empezar un rango con la pieza 14 como primera, y al aceptarlo solo falta elegir la última

#### Scenario: Re-anclar el rango conserva el hallazgo
- **WHEN** el usuario empezó un rango desde la pieza 14 con un hallazgo elegido y toca una pieza de otra fila
- **THEN** el rango empieza en esa pieza y el hallazgo elegido se conserva

#### Scenario: Destino que no se deduce
- **WHEN** el usuario tocó una pieza completa y elige un hallazgo de superficie o uno entre piezas
- **THEN** el diálogo ofrece "Elegir en el odontograma"
- **AND** al aceptarlo el diálogo se cierra, un aviso indica qué hallazgo se ubica y qué tocar, y al tocar una zona
  válida se abre su registro con ese hallazgo elegido

#### Scenario: En modo ubicar solo responden las zonas válidas
- **WHEN** el modo ubicar está activo para un hallazgo de superficie
- **THEN** solo las caras de las piezas responden, con el mouse y con el teclado
- **AND** los números de pieza, los espacios entre piezas y los botones de arco quedan atenuados y no responden

#### Scenario: Cancelar el modo ubicar
- **WHEN** el usuario pulsa "Cancelar" en el aviso, presiona Escape o cambia de pantalla
- **THEN** el modo ubicar termina sin registrar nada
- **AND** el siguiente registro no trae el hallazgo que se estaba ubicando

#### Scenario: Buscar desde la barra del odontograma
- **WHEN** el usuario pulsa "Buscar hallazgo" sin haber tocado el gráfico y elige un tipo
- **THEN** se activa el modo ubicar para ese tipo

#### Scenario: Búsqueda sin resultados
- **WHEN** el usuario escribe un nombre que no está en el catálogo
- **THEN** el selector lo indica y conserva lo que ya se había completado

#### Scenario: Nada cambia sin confirmar
- **WHEN** el diálogo ofrece ir a otra zona y el usuario no lo acepta
- **THEN** la localización tocada se mantiene y no se registra ningún hallazgo

#### Scenario: En edición solo los tipos de la zona actual
- **WHEN** el usuario edita un hallazgo registrado en una cara
- **THEN** el selector lista solo tipos de superficie y no ofrece tipos de otras zonas

### Requirement: Elegir la localización en pantallas táctiles

Al tocar con el dedo una pieza del odontograma editable, el sistema SHALL abrir esa pieza ampliada con sus cinco caras, la pieza completa y los espacios con sus vecinas, cada opción con su nombre y un área táctil de al menos 44 px. Elegir una SHALL abrir el registro con esa localización. Con mouse, lápiz o teclado, la interacción SHALL seguir yendo directo a la cara, la pieza o el espacio. SHALL respetar el modo ubicar y el modo rango.

#### Scenario: Tocar una pieza en el celular la amplía
- **WHEN** un usuario toca con el dedo la pieza 16 de un odontograma abierto
- **THEN** se abre la pieza 16 ampliada con sus caras vestibular, palatina, mesial, distal y oclusal, "Toda la pieza" y los espacios con la 15 y la 17
- **AND** ninguna de esas opciones mide menos de 44 px

#### Scenario: Elegir una cara en la pieza ampliada
- **WHEN** el usuario elige la cara oclusal en la pieza 16 ampliada
- **THEN** el panel se cierra y se abre el registro de hallazgos de la pieza 16, cara oclusal

#### Scenario: Elegir un espacio entre piezas en la pieza ampliada
- **WHEN** el usuario elige el espacio entre la 16 y la 15 en la pieza 16 ampliada
- **THEN** el panel se cierra y se abre el registro de hallazgos entre la 16 y la 15

#### Scenario: Una pieza del extremo de la fila tiene una sola vecina
- **WHEN** el usuario amplía la pieza 18
- **THEN** el panel ofrece solo el espacio con la 17

#### Scenario: La pieza ampliada muestra lo ya registrado
- **WHEN** el usuario amplía una pieza que tiene hallazgos registrados
- **THEN** el panel los lista y marca las caras que tienen hallazgos

#### Scenario: Cerrar sin elegir
- **WHEN** el usuario cierra la pieza ampliada sin elegir una opción
- **THEN** no se abre el registro y el odontograma queda como estaba

#### Scenario: Con mouse no cambia nada
- **WHEN** un usuario hace click con el mouse en una cara de una pieza, aunque su pantalla también sea táctil
- **THEN** se abre directamente el registro de esa cara, sin pieza ampliada

#### Scenario: Con el teclado no cambia nada, aunque antes se haya tocado con el dedo
- **WHEN** un usuario que antes tocó una pieza con el dedo llega con Tab a una cara y presiona Enter
- **THEN** se abre directamente el registro de esa cara, sin pieza ampliada

#### Scenario: En modo ubicar solo se ofrece lo que admite el alcance
- **WHEN** el usuario está ubicando un hallazgo de superficie y toca una pieza con el dedo
- **THEN** la pieza ampliada ofrece solo sus caras, y "Toda la pieza" y los espacios no están disponibles

#### Scenario: En modo ubicar de pieza completa va directo
- **WHEN** el usuario está ubicando un hallazgo de pieza completa y toca una pieza con el dedo
- **THEN** se abre directamente el registro de esa pieza, sin pieza ampliada

#### Scenario: En modo ubicar entre piezas la pieza abre sus espacios
- **WHEN** el usuario está ubicando un hallazgo entre piezas y toca una pieza con el dedo
- **THEN** la pieza ampliada ofrece solo sus espacios con las vecinas, y las caras y "Toda la pieza" no están disponibles

#### Scenario: En modo rango el toque ancla la pieza
- **WHEN** el usuario está registrando un rango y toca una pieza con el dedo
- **THEN** la pieza queda como ancla del rango, sin abrir la pieza ampliada

#### Scenario: En un odontograma sellado no se amplía
- **WHEN** un usuario toca con el dedo una pieza de un odontograma sellado
- **THEN** no se abre la pieza ampliada ni el registro
