# staff Specification

## Purpose
Gestión del personal de una clínica por su administrador: invitar personas a la organización, seguir y
administrar las invitaciones emitidas, y ver y administrar a quienes ya pertenecen a ella. Opera sobre las
invitaciones y las membresías de la organización activa; el alta de la cuenta a partir de una invitación la
define `authentication`, y el modelo de organización y membresía, `multi-tenancy`.

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
