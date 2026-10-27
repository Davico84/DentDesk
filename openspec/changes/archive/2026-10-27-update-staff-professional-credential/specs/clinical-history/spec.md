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

#### Scenario: Retiro confirmado mientras se sella
- **WHEN** el `ADMIN` retira la capacidad clínica de un miembro, o lo desactiva, mientras ese miembro sella una nota o cierra una admisión
- **THEN** o el sellado termina primero y queda con la credencial vigente en ese momento, o el retiro termina primero y el sellado se rechaza
- **AND** nunca queda sellado un registro después de que el retiro se confirmó

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

#### Scenario: Ver una Historia sellada antes del cambio
- **WHEN** un usuario abre una Historia Clínica `COMPLETED` antes de que existiera la credencial profesional
- **THEN** la interfaz la muestra sellada e indica que se firmó antes del registro de credenciales

#### Scenario: Una Historia sellada antes del cambio no se modifica ni en la base
- **WHEN** se intenta modificar una Historia Clínica sellada antes del cambio, aun escribiendo directo en la base
- **THEN** la base lo rechaza y la Historia no cambia

#### Scenario: Lo sellado antes del cambio no se reescribe
- **WHEN** un profesional declara su credencial después de haber sellado notas antes del cambio
- **THEN** esas notas siguen sin credencial del firmante
- **AND** siguen indicando que se firmaron antes del registro de credenciales

#### Scenario: Un sellado nuevo sin la foto es imposible
- **WHEN** se intenta guardar una Historia o una nota selladas después del cambio sin la credencial del firmante, aun escribiendo directo en la base
- **THEN** la base lo rechaza

## MODIFIED Requirements

### Requirement: Sellado a COMPLETED con checklist mínimo afirmativo
La transición de `DRAFT` a `COMPLETED` SHALL ser manual, explícita y **unidireccional** (sin volver a `DRAFT` ni resellar), y SHALL validar un **checklist mínimo**. En alergias, medicación y antecedentes familiares un campo vacío NO SHALL contar como revisado: la ausencia SHALL registrarse de forma **afirmativa**. El checklist SHALL exigir el **odontograma inicial** del paciente y ambos documentos SHALL sellarse de forma **atómica**: si falla el odontograma, la Historia SHALL seguir en `DRAFT`.

#### Scenario: Completar cuando el checklist está satisfecho
- **WHEN** un usuario con capacidad clínica solicita marcar como `COMPLETED` una Historia Clínica cuyo checklist mínimo está cubierto y cuyo paciente tiene el odontograma inicial registrado
- **THEN** el sistema sella la Historia en estado `COMPLETED` **y** el odontograma inicial, en una sola operación

#### Scenario: Rechazar el sellado con un ítem crítico faltante
- **WHEN** un usuario intenta marcar `COMPLETED` una Historia Clínica a la que le falta un ítem del checklist mínimo
- **THEN** el sistema rechaza la transición e indica qué ítems faltan

#### Scenario: Rechazar el sellado sin odontograma inicial
- **WHEN** un usuario intenta marcar `COMPLETED` una Historia Clínica cuyo paciente no tiene odontograma inicial registrado
- **THEN** el sistema rechaza la transición e indica que falta el odontograma

#### Scenario: Un fallo al sellar el odontograma deja la Historia en DRAFT
- **WHEN** el sellado del odontograma inicial falla durante la operación de completar la Historia Clínica
- **THEN** el sistema deja la Historia en `DRAFT` y el odontograma sin sellar, sin aplicar ninguno de los dos cambios

#### Scenario: Un campo de alergias vacío no equivale a "sin hallazgos"
- **WHEN** un usuario deja el campo de alergias vacío (sin afirmar "sin alergias conocidas") e intenta completar
- **THEN** el sistema rechaza el sellado porque el ítem no fue registrado afirmativamente

#### Scenario: Un campo de medicación vacío no equivale a "no toma medicación"
- **WHEN** un usuario deja la medicación sin registrar afirmativamente (ni un detalle ni "no toma medicación") e intenta completar
- **THEN** el sistema rechaza el sellado porque el ítem no fue registrado afirmativamente

#### Scenario: Rechazar una transición inválida
- **WHEN** un usuario intenta cambiar una Historia de `COMPLETED` a `DRAFT`, o volver a ejecutar el sellado sobre una ya `COMPLETED`
- **THEN** el sistema rechaza la operación y conserva el estado `COMPLETED`

#### Scenario: Las Historias selladas antes de esta regla no se ven afectadas
- **WHEN** se consulta una Historia Clínica que fue sellada antes de que el odontograma fuera obligatorio
- **THEN** el sistema la muestra como `COMPLETED` sin exigirle un odontograma retroactivamente

### Requirement: Sellado irreversible de la nota

El sellado SHALL ser explícito y **unidireccional** (`ABIERTA → SELLADA`), y SHALL exigir el contenido obligatorio y que quien sella tenga capacidad clínica en la clínica activa. Una nota sellada SHALL ser inmutable: el servidor SHALL rechazar toda modificación o descarte, aun por API directa, y una escritura concurrente con el sellado NO SHALL alterarla.

#### Scenario: Sellar una nota completa
- **WHEN** un usuario con capacidad clínica sella una nota `ABIERTA` con motivo, examen, tratamiento y al menos un diagnóstico
- **THEN** el sistema la pasa a `SELLADA` y registra cuándo y quién la selló, con la credencial del firmante

#### Scenario: Rechazar el sellado con contenido obligatorio faltante
- **WHEN** un usuario intenta sellar una nota sin examen o sin ningún diagnóstico
- **THEN** el sistema rechaza el sellado e indica qué falta

#### Scenario: Rechazar cambios a una nota sellada aun por API directa
- **WHEN** se envía directo a la API una petición para editar un campo, agregar o quitar un diagnóstico o antecedente, o descartar una nota `SELLADA`
- **THEN** el servidor rechaza la operación y la nota no cambia

#### Scenario: Rechazar un segundo sellado
- **WHEN** un usuario intenta sellar una nota que ya está `SELLADA`
- **THEN** el sistema rechaza la operación y conserva el sellado original

#### Scenario: Una escritura concurrente no altera la nota que se sella
- **WHEN** un usuario guarda un campo o un diagnóstico de una nota `ABIERTA` mientras otra operación la está sellando
- **THEN** el sistema rechaza la escritura porque la nota quedó sellada
- **AND** la nota sellada conserva el contenido que tenía al sellarse
