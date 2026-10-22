## ADDED Requirements

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
