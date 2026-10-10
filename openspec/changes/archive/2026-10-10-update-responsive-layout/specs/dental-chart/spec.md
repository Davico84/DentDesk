# Spec Delta — dental-chart

## ADDED Requirements

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
