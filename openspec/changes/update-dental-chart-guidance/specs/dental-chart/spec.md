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

#### Scenario: Con el odontograma sellado no hay guía
- **WHEN** el usuario abre un odontograma sellado
- **THEN** la guía no se abre sola y el botón no se ofrece

### Requirement: Orientación cuando el hallazgo se registra en otra localización

En el diálogo de registro, el sistema SHALL permitir buscar cualquier tipo del catálogo por nombre, sin distinguir
tildes. Si el tipo admite la localización tocada, SHALL elegirlo. Si no, SHALL indicar dónde se registra y ofrecer ir
allí cuando se deduce de lo tocado (pieza completa desde una cara, arco de esa pieza, rango que empieza en ella); si no
se deduce, SHALL explicar qué tocar. SHALL NOT cambiar de localización sin la confirmación del usuario.

#### Scenario: El hallazgo buscado admite la localización tocada
- **WHEN** el usuario tocó una cara y busca "caries"
- **THEN** el diálogo elige caries para esa cara

#### Scenario: Desde una cara, un hallazgo de pieza completa
- **WHEN** el usuario tocó una cara de la pieza 16 y busca "corona"
- **THEN** el diálogo explica que la corona se registra sobre la pieza completa
- **AND** ofrece registrarla en la pieza 16, y al aceptarlo abre el registro de la pieza completa 16 con corona elegida,
  directamente en el formulario aunque la pieza 16 ya tenga otros hallazgos

#### Scenario: Desde una pieza, un hallazgo de arco
- **WHEN** el usuario tocó la pieza 36 y busca un hallazgo que se registra sobre un arco completo
- **THEN** el diálogo ofrece registrarlo en el arco inferior, que es el de esa pieza
- **AND** al aceptarlo abre el registro del arco inferior con ese hallazgo elegido

#### Scenario: Desde una pieza, un hallazgo de rango
- **WHEN** el usuario tocó la pieza 14 y busca un hallazgo que se registra sobre un rango de piezas
- **THEN** el diálogo ofrece empezar un rango con la pieza 14 como primera, y al aceptarlo solo falta elegir la última

#### Scenario: Cancelar el rango empezado desde el diálogo
- **WHEN** el usuario aceptó empezar un rango desde la pieza 14 y luego sale del modo rango sin elegir la última pieza
- **THEN** no se registra ningún hallazgo
- **AND** la próxima vez que use "Registrar rango" el diálogo no trae el hallazgo que había buscado

#### Scenario: La localización no se puede deducir
- **WHEN** el usuario tocó una pieza completa y busca un hallazgo de superficie o uno entre piezas
- **THEN** el diálogo explica qué tocar (una cara de la pieza, o el espacio junto a ella) sin elegir por el usuario

#### Scenario: Búsqueda sin resultados
- **WHEN** el usuario busca un nombre que no está en el catálogo
- **THEN** el diálogo lo indica y conserva lo que ya se había completado

#### Scenario: Nada cambia sin confirmar
- **WHEN** el diálogo ofrece ir a otra localización y el usuario no lo acepta
- **THEN** la localización tocada se mantiene y no se registra ningún hallazgo
