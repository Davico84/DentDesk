# Spec Delta

## ADDED Requirements

### Requirement: Credencial del firmante en el odontograma sellado

Al sellarse con el cierre de la admisión, el odontograma inicial SHALL guardar quién lo firmó, su nombre, su número COP y su fecha "habilitado hasta", copiados en ese momento y sin cambiar después. Un odontograma sellado antes de que existiera la credencial profesional SHALL conservarse sin cambios y mostrarse como *firmado antes del registro de credenciales*.

#### Scenario: El odontograma sellado guarda al firmante
- **WHEN** un usuario con capacidad clínica cierra la admisión de un paciente
- **THEN** el odontograma inicial sellado guarda su identificador, su nombre, su número COP y su fecha "habilitado hasta" de ese momento
- **AND** son los mismos datos que guarda la Historia Clínica sellada en esa operación

#### Scenario: Ver quién firmó el odontograma
- **WHEN** un usuario abre un odontograma inicial sellado
- **THEN** la interfaz muestra el nombre del firmante y su número COP

#### Scenario: Odontograma sellado antes del cambio
- **WHEN** un usuario abre un odontograma sellado antes de que existiera la credencial profesional
- **THEN** la interfaz lo muestra sellado e indica que se firmó antes del registro de credenciales

#### Scenario: Un sellado nuevo sin la foto es imposible
- **WHEN** se intenta guardar un odontograma sellado después del cambio sin la credencial del firmante, aun escribiendo directo en la base
- **THEN** la base lo rechaza
