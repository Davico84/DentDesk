## ADDED Requirements

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
