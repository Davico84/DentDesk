# staff Specification

## Purpose
Gestión del personal de una clínica por su administrador: invitar personas a la organización, seguir y
administrar las invitaciones emitidas, y ver y administrar a quienes ya pertenecen a ella, incluida su capacidad
clínica en la clínica. Cada profesional declara su propia credencial (colegiatura COP y hasta cuándo está
habilitado), que con la membresía activa y no retirada define si puede firmar actos clínicos. Opera sobre las
invitaciones, las membresías de la organización activa y la credencial; el alta de la cuenta a partir de una
invitación la define `authentication`, y el modelo de organización y membresía, `multi-tenancy`.

## Requirements

### Requirement: Gestión del personal exclusiva del administrador de la organización activa

El sistema SHALL permitir la gestión del personal solo a un `ADMIN` de la organización activa, y SHALL limitar
toda lectura y acción a esa organización. Un `MEMBER` SHALL recibir acceso denegado; un visitante o una sesión
de plataforma SHALL ser rechazado como no autenticado.

#### Scenario: Un miembro intenta gestionar el personal
- **WHEN** un usuario con rol `MEMBER` consulta el personal, las invitaciones o intenta invitar
- **THEN** el sistema responde con acceso denegado
- **AND** no muestra ni modifica nada

#### Scenario: Un visitante intenta gestionar el personal
- **WHEN** una petición sin sesión llega a cualquier operación de gestión del personal
- **THEN** el sistema la rechaza como no autenticada

#### Scenario: Una sesión de plataforma intenta gestionar el personal
- **WHEN** una petición con la sesión de un administrador de plataforma llega a la gestión del personal
- **THEN** el sistema la rechaza como no autenticada

#### Scenario: Un administrador no ve otra clínica
- **WHEN** el `ADMIN` de la clínica A consulta el personal o las invitaciones
- **THEN** solo obtiene los de la clínica A
- **AND** una acción sobre una invitación o un miembro de la clínica B responde como recurso inexistente

### Requirement: Invitar personal

El sistema SHALL permitir al `ADMIN` invitar por correo a una persona con rol `MEMBER`. La invitación SHALL
enviarse por correo y su enlace SHALL entregarse solo ahí, nunca en la respuesta. SHALL rechazar con conflicto
un correo que ya es miembro o que ya tiene una invitación pendiente o vencida, y validar el formato del correo
antes de enviar nada.

#### Scenario: Invitación emitida
- **WHEN** el `ADMIN` invita un correo válido que no pertenece a la clínica
- **THEN** el sistema crea una invitación pendiente con rol `MEMBER` y envía el correo
- **AND** la respuesta no incluye el enlace ni el código

#### Scenario: El correo ya es miembro
- **WHEN** el `ADMIN` invita un correo que ya pertenece a la clínica
- **THEN** el sistema responde con un conflicto y no crea ninguna invitación

#### Scenario: Ya hay una invitación pendiente para ese correo
- **WHEN** el `ADMIN` invita un correo que tiene una invitación pendiente en la clínica
- **THEN** el sistema responde con un conflicto que sugiere reenviar la existente
- **AND** no crea una segunda invitación

#### Scenario: La invitación anterior de ese correo venció
- **WHEN** el `ADMIN` invita un correo cuya invitación en la clínica está vencida
- **THEN** el sistema responde con un conflicto que indica que la invitación vencida puede reenviarse
- **AND** no crea una segunda invitación

#### Scenario: Correo con formato inválido
- **WHEN** el `ADMIN` escribe un correo con formato inválido
- **THEN** el formulario muestra el error y no envía la petición

#### Scenario: El correo no se puede enviar
- **WHEN** el envío del correo falla
- **THEN** el sistema informa que no se pudo enviar
- **AND** no queda ninguna invitación creada

### Requirement: Listado de invitaciones con su estado

El sistema SHALL listar al `ADMIN` las invitaciones de su organización, de la más reciente a la más antigua, con
correo, rol, fecha de envío, vencimiento y estado: pendiente, aceptada, revocada o vencida. Una invitación SHALL
mostrarse vencida cuando sigue pendiente pero su vigencia terminó. El listado SHALL NOT exponer enlaces ni
códigos.

#### Scenario: Ver las invitaciones emitidas
- **WHEN** el `ADMIN` abre la pestaña de invitaciones
- **THEN** ve cada invitación de su clínica con su correo, rol, fechas y estado
- **AND** ninguna respuesta incluye el enlace ni el código de verificación

#### Scenario: Invitación vencida
- **WHEN** una invitación pendiente supera su fecha de vencimiento
- **THEN** el listado la muestra como vencida

#### Scenario: Invitación aceptada
- **WHEN** el invitado completa el registro
- **THEN** el listado muestra la invitación como aceptada

#### Scenario: Sin invitaciones
- **WHEN** la clínica no emitió ninguna invitación
- **THEN** la pestaña muestra un estado vacío que invita a emitir la primera

### Requirement: Filtro de invitaciones por estado

La pestaña de invitaciones SHALL permitir filtrar por estado (todas, pendientes, aceptadas, vencidas, revocadas)
y SHALL indicar cuántas hay en cada uno.

#### Scenario: Filtrar las pendientes
- **WHEN** el `ADMIN` elige el filtro "Pendientes"
- **THEN** solo ve las invitaciones pendientes y no vencidas

#### Scenario: Filtro sin resultados
- **WHEN** el filtro elegido no tiene invitaciones
- **THEN** la pestaña lo indica sin confundirlo con una clínica sin invitaciones

### Requirement: Reenviar una invitación

El sistema SHALL permitir al `ADMIN` reenviar una invitación `MEMBER` pendiente o vencida: SHALL emitir un enlace
y un código nuevos, anular los anteriores, renovar la vigencia completa y reiniciar la verificación del correo.
SHALL rechazar con conflicto reenviar una invitación aceptada o revocada. Si el correo no sale, la invitación
SHALL quedar como estaba.

#### Scenario: Reenviar una invitación cuyo código venció
- **WHEN** el `ADMIN` reenvía una invitación pendiente cuyo código de verificación ya venció
- **THEN** el invitado recibe un correo con un enlace y un código nuevos
- **AND** puede verificar el correo y registrarse con ellos

#### Scenario: El enlace anterior deja de funcionar
- **WHEN** el invitado usa el enlace o el código del correo anterior al reenvío
- **THEN** el sistema los rechaza como invitación inválida

#### Scenario: Reenviar una invitación vencida
- **WHEN** el `ADMIN` reenvía una invitación vencida
- **THEN** la invitación vuelve a estar pendiente con una vigencia nueva

#### Scenario: Reenviar una invitación aceptada o revocada
- **WHEN** el `ADMIN` intenta reenviar una invitación aceptada o revocada
- **THEN** el sistema responde con un conflicto y no envía ningún correo

#### Scenario: El reenvío no se puede enviar
- **WHEN** falla el envío del correo de un reenvío
- **THEN** el sistema informa el fallo
- **AND** la invitación conserva el estado, el enlace y el código que tenía antes del reenvío

#### Scenario: Reenvío y registro al mismo tiempo
- **WHEN** el invitado completa el registro con el enlace anterior mientras el `ADMIN` reenvía la invitación
- **THEN** solo uno de los dos tiene efecto: o el registro se acepta y el reenvío responde con un conflicto, o el
  reenvío se aplica y el registro con el enlace anterior se rechaza
- **AND** una invitación aceptada nunca vuelve a quedar pendiente

#### Scenario: Invitación de administrador
- **WHEN** el `ADMIN` ve una invitación con rol `ADMIN` emitida por la plataforma
- **THEN** la puede consultar, pero no reenviarla ni revocarla

### Requirement: Revocar una invitación

El sistema SHALL permitir al `ADMIN` revocar, con confirmación, una invitación `MEMBER` pendiente o vencida; su
enlace SHALL dejar de funcionar. SHALL rechazar con conflicto revocar una invitación aceptada o ya revocada.

#### Scenario: Revocar una invitación pendiente
- **WHEN** el `ADMIN` confirma la revocación de una invitación pendiente
- **THEN** la invitación queda revocada
- **AND** su enlace y su código ya no permiten registrarse

#### Scenario: Cancelar la revocación
- **WHEN** el `ADMIN` cierra la confirmación sin aceptar
- **THEN** la invitación sigue pendiente

#### Scenario: Revocación y registro al mismo tiempo
- **WHEN** el invitado completa el registro mientras el `ADMIN` revoca la invitación
- **THEN** solo uno de los dos tiene efecto: o la persona queda registrada y la revocación responde con un
  conflicto, o la invitación queda revocada y el registro se rechaza

#### Scenario: Revocar una invitación aceptada
- **WHEN** el `ADMIN` intenta revocar una invitación ya aceptada
- **THEN** el sistema responde con un conflicto
- **AND** la persona sigue siendo miembro

### Requirement: Listado del personal con su estado

El sistema SHALL listar al `ADMIN` los miembros de su organización con nombre, correo, rol y estado (activo o
inactivo), y SHALL permitir buscarlos por nombre o correo sin distinguir mayúsculas ni tildes.

#### Scenario: Ver al personal
- **WHEN** el `ADMIN` abre la pestaña de personal
- **THEN** ve a cada miembro de su clínica con su nombre, correo, rol y estado
- **AND** se ve a sí mismo en la lista

#### Scenario: Buscar por nombre o correo
- **WHEN** el `ADMIN` escribe "maria" en el buscador
- **THEN** la lista muestra solo a los miembros cuyo nombre o correo lo contienen, incluida "María"

#### Scenario: Búsqueda sin resultados
- **WHEN** ningún miembro coincide con la búsqueda
- **THEN** la pestaña lo indica y permite limpiar la búsqueda

### Requirement: Desactivar y reactivar a un miembro

El sistema SHALL permitir al `ADMIN` desactivar, con confirmación, a un miembro `MEMBER` de su organización, y
reactivarlo. Un miembro inactivo SHALL NOT poder iniciar sesión ni renovar la sesión en esa clínica; su historial
y lo que registró SHALL conservarse. El `ADMIN` SHALL NOT desactivarse a sí mismo ni a otro `ADMIN`.

#### Scenario: Desactivar a un miembro
- **WHEN** el `ADMIN` confirma la desactivación de un miembro activo
- **THEN** el miembro queda inactivo
- **AND** ya no puede iniciar sesión ni renovar su sesión en la clínica
- **AND** los registros clínicos que creó siguen visibles y atribuidos a él

#### Scenario: Reactivar a un miembro
- **WHEN** el `ADMIN` reactiva a un miembro inactivo
- **THEN** el miembro vuelve a estar activo y puede iniciar sesión

#### Scenario: Intentar desactivarse a sí mismo
- **WHEN** el `ADMIN` intenta cambiar el estado de su propia cuenta
- **THEN** el sistema responde con un conflicto y su estado no cambia
- **AND** la interfaz no ofrece esa acción sobre su propia fila

#### Scenario: Intentar desactivar a un administrador
- **WHEN** el `ADMIN` intenta desactivar a otro `ADMIN`
- **THEN** el sistema responde con acceso denegado y su estado no cambia

#### Scenario: El miembro no pertenece a la clínica
- **WHEN** el `ADMIN` intenta cambiar el estado de alguien que no es miembro de su clínica
- **THEN** el sistema responde como recurso inexistente

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

#### Scenario: El miembro no pertenece a la clínica
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
