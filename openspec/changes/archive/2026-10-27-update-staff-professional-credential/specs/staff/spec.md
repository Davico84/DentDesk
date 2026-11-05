# Spec Delta

## ADDED Requirements

### Requirement: Declarar la credencial profesional propia

El sistema SHALL permitir a cada usuario declarar, solo para sí mismo, su número de colegiatura COP y la fecha hasta la que está habilitado, aceptando una declaración jurada. Renovar SHALL ser declarar una fecha nueva. El número SHALL tener solo dígitos y no pertenecer a otro usuario; la fecha SHALL NOT ser anterior a hoy. Nadie SHALL declarar ni modificar la credencial de otro usuario.

#### Scenario: Declarar la credencial por primera vez
- **WHEN** un usuario sin credencial declara un número COP y una fecha de habilitación futura y acepta la declaración jurada
- **THEN** el sistema guarda la credencial a su nombre
- **AND** la credencial vale en todas las clínicas a las que pertenece

#### Scenario: La misma credencial al cambiar de clínica
- **WHEN** un usuario que declaró su credencial con la clínica A activa cambia a la clínica B
- **THEN** con la clínica B activa ve la misma credencial, sin volver a declararla

#### Scenario: Renovar la habilitación
- **WHEN** un usuario con la habilitación vencida declara una fecha de habilitación nueva
- **THEN** el sistema actualiza la fecha y el usuario vuelve a estar habilitado

#### Scenario: Declarar sin aceptar la declaración jurada
- **WHEN** un usuario envía la credencial sin aceptar la declaración jurada
- **THEN** el sistema la rechaza y no guarda nada

#### Scenario: Número con caracteres que no son dígitos
- **WHEN** un usuario declara un número COP con letras o símbolos
- **THEN** el formulario muestra el error y no envía la petición
- **AND** el servidor también lo rechaza si la petición llega directo

#### Scenario: Fecha de habilitación ya pasada
- **WHEN** un usuario declara una fecha "habilitado hasta" anterior a hoy
- **THEN** el sistema la rechaza e indica que la fecha debe ser hoy o posterior

#### Scenario: Número declarado por otro usuario
- **WHEN** un usuario declara un número COP que ya figura en la credencial de otro usuario
- **THEN** el sistema responde con un conflicto de texto fijo
- **AND** ni la respuesta ni los registros del servidor revelan quién lo declaró, en qué clínica ni el número en conflicto

#### Scenario: Intentar declarar la credencial de otro usuario
- **WHEN** una petición intenta crear o modificar la credencial de un usuario distinto al de la sesión, aun por API directa y aun siendo `ADMIN`
- **THEN** el sistema la rechaza y la credencial de ese usuario no cambia

#### Scenario: Sesión de plataforma
- **WHEN** una petición con la sesión de un administrador de plataforma llega a la credencial profesional
- **THEN** el sistema la rechaza como no autenticada

### Requirement: Capacidad clínica efectiva en la clínica activa

Un usuario SHALL tener capacidad clínica en la clínica activa solo si su membresía está activa, tiene credencial declarada, su fecha "habilitado hasta" no pasó en la hora de Perú y su capacidad no fue retirada en esa membresía. El usuario SHALL poder consultar su propia credencial y su capacidad clínica, con el motivo cuando no la tiene: membresía inactiva, sin credencial, habilitación vencida o capacidad retirada.

#### Scenario: Habilitado y sin retiro
- **WHEN** un usuario con credencial vigente y sin retiro en la clínica activa consulta su capacidad clínica
- **THEN** el sistema indica que tiene capacidad clínica y hasta qué fecha está habilitado

#### Scenario: Sin credencial declarada
- **WHEN** un usuario que nunca declaró su credencial consulta su capacidad clínica
- **THEN** el sistema indica que no tiene capacidad clínica porque no declaró su credencial

#### Scenario: La habilitación vence sola
- **WHEN** un usuario consulta su capacidad clínica el día siguiente a su fecha "habilitado hasta" en la hora de Perú
- **THEN** el sistema indica que no tiene capacidad clínica porque su habilitación venció en esa fecha
- **AND** nadie tuvo que cambiar nada para que deje de tenerla

#### Scenario: Último día de habilitación
- **WHEN** un usuario consulta su capacidad clínica en su fecha "habilitado hasta", en la hora de Perú
- **THEN** el sistema indica que tiene capacidad clínica

#### Scenario: Clínicas con otra zona horaria
- **WHEN** un usuario de dos clínicas con zonas horarias distintas consulta su capacidad en las dos en el mismo instante
- **THEN** el vencimiento es el mismo en las dos, porque se evalúa en la hora de Perú

#### Scenario: Consultar la credencial sin clínica activa
- **WHEN** un usuario sin clínica activa consulta o declara su credencial
- **THEN** el sistema la muestra o la guarda
- **AND** la capacidad clínica viene vacía, porque depende de una clínica

#### Scenario: Miembro desactivado con la sesión todavía abierta
- **WHEN** el `ADMIN` desactiva a un miembro y ese miembro, con su sesión aún vigente, consulta su capacidad o intenta firmar
- **THEN** el sistema indica que no tiene capacidad clínica porque su membresía está inactiva

#### Scenario: Retirada en una clínica, vigente en otra
- **WHEN** a un usuario le retiraron la capacidad clínica en la clínica A y consulta su capacidad con la clínica B activa
- **THEN** en la clínica B el sistema indica que tiene capacidad clínica
- **AND** en la clínica A indica que la capacidad fue retirada por la clínica

### Requirement: Retirar y restituir la capacidad clínica de un miembro

El sistema SHALL permitir al `ADMIN` retirar, con confirmación, la capacidad clínica de un `MEMBER` de su clínica, y restituirla. El retiro SHALL afectar solo a esa clínica y SHALL NOT tocar la credencial declarada. El `ADMIN` SHALL NOT otorgar capacidad clínica: restituir solo levanta el retiro. SHALL NOT retirarla a otro `ADMIN` ni a sí mismo.

#### Scenario: Retirar la capacidad clínica
- **WHEN** el `ADMIN` confirma el retiro de la capacidad clínica de un miembro habilitado
- **THEN** el miembro deja de tener capacidad clínica en esa clínica
- **AND** su credencial declarada no cambia

#### Scenario: Cancelar el retiro
- **WHEN** el `ADMIN` cierra la confirmación sin aceptar
- **THEN** la capacidad clínica del miembro no cambia

#### Scenario: Restituir la capacidad clínica
- **WHEN** el `ADMIN` restituye la capacidad de un miembro con credencial vigente al que se la había retirado
- **THEN** el miembro vuelve a tener capacidad clínica en esa clínica

#### Scenario: Restituir no otorga lo que no hay
- **WHEN** el `ADMIN` restituye la capacidad de un miembro cuya habilitación venció o que no declaró credencial
- **THEN** el retiro se levanta, pero el miembro sigue sin capacidad clínica por su credencial

#### Scenario: Retirar la capacidad de un administrador
- **WHEN** el `ADMIN` intenta retirar la capacidad clínica de otro `ADMIN` o la suya propia
- **THEN** el sistema responde con acceso denegado y la capacidad no cambia

#### Scenario: Un miembro intenta retirar la capacidad
- **WHEN** un `MEMBER` intenta retirar o restituir la capacidad clínica de alguien
- **THEN** el sistema responde con acceso denegado

#### Scenario: Una membresía no nace retirada
- **WHEN** se intenta crear una membresía que ya trae la capacidad clínica retirada, aun escribiendo directo en la base
- **THEN** la base lo rechaza

#### Scenario: Un miembro intenta levantar su propio retiro
- **WHEN** un miembro al que le retiraron la capacidad clínica intenta levantar el retiro, aun escribiendo directo en la base con su sesión
- **THEN** el sistema lo rechaza y el retiro sigue vigente

#### Scenario: Retirar la capacidad de quien no es miembro de la clínica
- **WHEN** el `ADMIN` intenta retirar la capacidad de alguien que no es miembro de su clínica
- **THEN** el sistema responde como recurso inexistente

### Requirement: Capacidad clínica en el listado del personal

El listado del personal SHALL mostrar al `ADMIN`, por cada miembro de su clínica, el estado de su capacidad clínica: sin credencial, habilitado hasta una fecha, habilitación vencida o retirada por la clínica, con su número COP cuando lo declaró. SHALL NOT exponer la credencial de quien no es miembro de la clínica.

#### Scenario: Ver la capacidad clínica del personal
- **WHEN** el `ADMIN` abre la pestaña de personal
- **THEN** ve, en cada miembro, su estado de capacidad clínica y su número COP si lo declaró

#### Scenario: Miembro con la habilitación vencida
- **WHEN** la fecha "habilitado hasta" de un miembro ya pasó
- **THEN** el listado lo muestra con la habilitación vencida y la fecha en que venció

#### Scenario: Credencial de alguien de otra clínica
- **WHEN** el `ADMIN` de la clínica A consulta el personal
- **THEN** no obtiene la credencial de ningún usuario que no sea miembro de la clínica A
