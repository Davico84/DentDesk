# Spec Delta

## ADDED Requirements

### Requirement: Firmar exige capacidad clínica

Cerrar la admisión (sellar la Historia Clínica a `COMPLETED` junto con el odontograma inicial) y sellar una nota de consulta SHALL exigir que quien firma tenga capacidad clínica en la clínica activa. Sin ella, el servidor SHALL rechazar con acceso denegado e indicar el motivo, aun por API directa, y SHALL NOT sellar nada. Abrir, editar y descartar borradores no cambia.

#### Scenario: Cerrar la admisión con capacidad clínica
- **WHEN** un usuario con capacidad clínica cierra una admisión cuyo checklist está cubierto
- **THEN** el sistema sella la Historia Clínica y el odontograma inicial

#### Scenario: Cerrar la admisión sin credencial declarada
- **WHEN** un usuario que no declaró su credencial intenta cerrar una admisión
- **THEN** el sistema responde con acceso denegado e indica que debe declarar su credencial profesional
- **AND** la Historia sigue en `DRAFT` y el odontograma sin sellar

#### Scenario: Sellar una nota con la habilitación vencida
- **WHEN** un usuario cuya fecha "habilitado hasta" ya pasó intenta sellar una nota `ABIERTA`
- **THEN** el sistema responde con acceso denegado e indica la fecha en que venció su habilitación
- **AND** la nota sigue `ABIERTA` con su contenido

#### Scenario: Sellar una nota con la capacidad retirada por la clínica
- **WHEN** un usuario al que la clínica activa le retiró la capacidad clínica intenta sellar una nota
- **THEN** el sistema responde con acceso denegado e indica que la clínica retiró su capacidad clínica

#### Scenario: Sin capacidad clínica se siguen cargando borradores
- **WHEN** un usuario sin capacidad clínica guarda la información de una Historia en `DRAFT` o un campo de una nota `ABIERTA`
- **THEN** el sistema guarda el cambio como hasta ahora

#### Scenario: La interfaz explica por qué no se puede firmar
- **WHEN** un usuario sin capacidad clínica abre una Historia lista para cerrar o una nota `ABIERTA`
- **THEN** la acción de firmar aparece deshabilitada con el motivo y un acceso para declarar su credencial

### Requirement: Credencial del firmante fotografiada al sellar

Al cerrar la admisión y al sellar una nota, el sistema SHALL guardar en la Historia Clínica o en la nota quién firmó, su nombre, su número COP y su fecha "habilitado hasta", **copiados en ese momento**. Un cambio posterior del nombre, la credencial, el retiro o la membresía del firmante SHALL NOT alterar lo guardado. Lo sellado SHALL mostrar al firmante y su número COP.

#### Scenario: La Historia sellada guarda al firmante
- **WHEN** un usuario con capacidad clínica cierra una admisión
- **THEN** la Historia Clínica sellada guarda su identificador, su nombre, su número COP y su fecha "habilitado hasta" de ese momento

#### Scenario: La nota sellada guarda al firmante
- **WHEN** un usuario con capacidad clínica sella una nota de consulta
- **THEN** la nota sellada guarda su identificador, su nombre, su número COP y su fecha "habilitado hasta" de ese momento

#### Scenario: Renovar la credencial no reescribe lo firmado
- **WHEN** un profesional que ya firmó una nota declara una fecha de habilitación nueva o corrige su nombre
- **THEN** la nota sellada sigue mostrando el nombre y la fecha que tenía al firmarla

#### Scenario: Perder la habilitación no invalida lo firmado
- **WHEN** la habilitación de un profesional vence o la clínica le retira la capacidad clínica después de que selló una nota
- **THEN** la nota sigue sellada y válida, con la credencial con que la firmó

#### Scenario: Ver quién firmó
- **WHEN** un usuario abre una nota sellada o una Historia Clínica `COMPLETED`
- **THEN** la interfaz muestra el nombre del firmante y su número COP

### Requirement: Firmas anteriores al registro de credenciales

Una Historia Clínica o una nota selladas antes de que existiera la credencial profesional SHALL conservarse sin cambios y SHALL mostrarse como *firmadas antes del registro de credenciales*. El sistema SHALL NOT completarles una credencial retroactivamente, y SHALL NOT aceptar un sellado nuevo sin la foto de la credencial.

#### Scenario: Ver una nota sellada antes del cambio
- **WHEN** un usuario abre una nota sellada antes de que existiera la credencial profesional
- **THEN** la interfaz la muestra sellada e indica que se firmó antes del registro de credenciales

#### Scenario: Lo sellado antes del cambio no se reescribe
- **WHEN** un profesional declara su credencial después de haber sellado notas antes del cambio
- **THEN** esas notas siguen sin credencial del firmante
- **AND** siguen indicando que se firmaron antes del registro de credenciales

#### Scenario: Un sellado nuevo sin la foto es imposible
- **WHEN** se intenta guardar una Historia o una nota selladas después del cambio sin la credencial del firmante, aun escribiendo directo en la base
- **THEN** la base lo rechaza
