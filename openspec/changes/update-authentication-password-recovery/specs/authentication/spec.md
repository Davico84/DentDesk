## ADDED Requirements

### Requirement: Pedir la recuperación de la contraseña

El sistema SHALL permitir a cualquier persona sin sesión pedir la recuperación de la contraseña indicando un correo. La respuesta SHALL ser siempre la misma en status, headers y cuerpo, y SHALL darse sin esperar a resolver la cuenta, exista o no y cualquiera sea su estado o su límite. Si corresponde enviar el enlace, el sistema SHALL enviarlo a ese correo y a ningún otro.

#### Scenario: Pedir con un correo registrado
- **WHEN** alguien pide la recuperación con el correo de una cuenta activa
- **THEN** el sistema responde que, si existe una cuenta con ese correo, le enviará un enlace
- **AND** envía el enlace a ese correo

#### Scenario: Pedir con un correo no registrado
- **WHEN** alguien pide la recuperación con un correo que no pertenece a ninguna cuenta
- **THEN** el sistema responde exactamente lo mismo que con un correo registrado
- **AND** no envía ningún correo

#### Scenario: La respuesta no delata la cuenta
- **WHEN** se pide la recuperación con el correo de una cuenta activa dentro del límite, uno no registrado, uno de una identidad desactivada, uno de un administrador de plataforma y uno de una cuenta que superó el límite
- **THEN** las cinco respuestas son idénticas en status, headers y cuerpo
- **AND** llegan sin esperar a que se busque la cuenta ni a que se envíe el correo

#### Scenario: Correo con mayúsculas o espacios
- **WHEN** alguien pide la recuperación con su correo en mayúsculas o con espacios en los extremos
- **THEN** el sistema lo trata como el mismo correo de la cuenta

#### Scenario: Correo con formato inválido o vacío
- **WHEN** alguien envía el pedido con el correo vacío o con un formato inválido
- **THEN** el formulario muestra el error y no envía la petición
- **AND** el servidor también lo rechaza si la petición llega directo

#### Scenario: Cuenta desactivada
- **WHEN** alguien pide la recuperación con el correo de una cuenta cuya identidad está desactivada
- **THEN** el sistema responde lo mismo que con cualquier otro correo
- **AND** no envía ningún correo

#### Scenario: Cuenta sin acceso activo a ninguna clínica
- **WHEN** alguien pide la recuperación con el correo de una cuenta activa cuyas membresías están todas inactivas
- **THEN** el sistema envía el enlace igual que a cualquier cuenta activa

### Requirement: Límite de enlaces de recuperación

El sistema SHALL enviar como máximo 3 enlaces de recuperación por cuenta en cualquier período de 24 horas. Superado el límite, el pedido SHALL recibir la misma respuesta que cualquier otro y SHALL NOT enviar correo.

#### Scenario: Tercer pedido del día
- **WHEN** una cuenta pide la recuperación por tercera vez en 24 horas
- **THEN** el sistema envía el enlace

#### Scenario: Cuarto pedido del día
- **WHEN** una cuenta pide la recuperación por cuarta vez en 24 horas
- **THEN** el sistema responde lo mismo que siempre
- **AND** no envía ningún correo

#### Scenario: El límite es móvil
- **WHEN** pasan 24 horas desde el primero de tres pedidos de una cuenta
- **THEN** la cuenta puede volver a recibir un enlace

### Requirement: Enlace de recuperación

Cada enlace de recuperación SHALL valer 30 minutos desde su emisión y SHALL poder usarse una sola vez. Emitir un enlace nuevo para una cuenta SHALL anular los anteriores que sigan sin usar. El sistema SHALL guardar solo un resumen irreversible del enlace, nunca el enlace mismo, y SHALL rechazar un enlace inválido, vencido, usado o anulado sin indicar cuál de los cuatro casos ocurrió.

#### Scenario: Enlace vigente
- **WHEN** alguien abre un enlace de recuperación emitido hace menos de 30 minutos y sin usar
- **THEN** el sistema le permite elegir una contraseña nueva

#### Scenario: Enlace vencido
- **WHEN** alguien usa un enlace emitido hace más de 30 minutos
- **THEN** el sistema lo rechaza indicando que el enlace no es válido o ya venció
- **AND** ofrece pedir uno nuevo

#### Scenario: Enlace ya usado
- **WHEN** alguien usa un enlace con el que ya se restableció la contraseña
- **THEN** el sistema lo rechaza con el mismo mensaje que un enlace vencido

#### Scenario: Enlace anulado por uno nuevo
- **WHEN** una cuenta pide un segundo enlace y alguien usa el primero
- **THEN** el sistema rechaza el primero con el mismo mensaje que un enlace vencido
- **AND** el segundo sigue siendo válido

#### Scenario: El mismo enlace usado dos veces a la vez
- **WHEN** el mismo enlace se envía dos veces al mismo tiempo con contraseñas distintas
- **THEN** solo una de las dos peticiones cambia la contraseña y la otra se rechaza

#### Scenario: El enlace no queda en el historial
- **WHEN** alguien abre un enlace de recuperación
- **THEN** la dirección visible y el historial del navegador dejan de mostrar el enlace
- **AND** la página no envía el enlace a otros sitios como referencia

### Requirement: Restablecer la contraseña con el enlace

Con un enlace vigente, el sistema SHALL permitir elegir una contraseña nueva de al menos 8 caracteres, como en el registro, confirmada. Restablecer SHALL NOT exigir la contraseña anterior. Un restablecimiento rechazado SHALL NOT cambiar nada ni consumir el enlace.

#### Scenario: Restablecer la contraseña
- **WHEN** alguien con un enlace vigente indica una contraseña nueva válida y confirmada
- **THEN** el sistema cambia la contraseña y consume el enlace
- **AND** el login siguiente funciona con la nueva y no con la anterior

#### Scenario: Nueva contraseña demasiado corta
- **WHEN** alguien indica una contraseña nueva de menos de 8 caracteres
- **THEN** el formulario muestra el error y no envía la petición
- **AND** el servidor también la rechaza si la petición llega directo, sin consumir el enlace

#### Scenario: La confirmación no coincide
- **WHEN** la confirmación no coincide con la contraseña nueva
- **THEN** el formulario muestra el error y no envía la petición

### Requirement: Cerrar todas las sesiones al restablecer la contraseña

Al restablecer la contraseña, el sistema SHALL cerrar todas las sesiones del usuario, en todos los dispositivos y clínicas, incluida la del dispositivo desde el que se restableció, y SHALL NOT abrir una sesión nueva: el usuario SHALL iniciar sesión con la contraseña nueva. Una sesión cerrada SHALL NOT poder renovarse; su acceso vigente SHALL terminar, a más tardar, cuando venza su token de acceso.

#### Scenario: Las sesiones abiertas quedan cerradas
- **WHEN** un usuario con sesiones abiertas restablece su contraseña
- **THEN** ninguna de esas sesiones puede renovarse y vuelven al login al vencer su acceso

#### Scenario: Volver al login después de restablecer
- **WHEN** un usuario restablece su contraseña
- **THEN** la interfaz lo lleva al login indicando que ya puede entrar con la contraseña nueva

#### Scenario: Una sesión que se inicia o renueva durante el restablecimiento también se cierra
- **WHEN** otra sesión del usuario se renueva, o alguien inicia sesión con la contraseña anterior, al mismo tiempo que el restablecimiento
- **THEN** esa sesión tampoco puede renovarse después, o el inicio de sesión se rechaza

#### Scenario: Un restablecimiento rechazado no cierra nada
- **WHEN** el restablecimiento se rechaza por cualquier motivo
- **THEN** ninguna sesión del usuario se cierra

### Requirement: Estado de la cuenta al restablecer la contraseña

Restablecer la contraseña SHALL NOT desbloquear una cuenta bloqueada temporalmente por intentos fallidos ni reactivar una identidad o un acceso desactivados. Tras restablecer, la interfaz SHALL informar hasta cuándo dura el bloqueo, si lo hay, o que debe contactar al administrador, si la identidad o el acceso a la clínica están desactivados. Sin bloqueo vigente, el contador de intentos fallidos SHALL volver a cero.

#### Scenario: Cuenta bloqueada por intentos fallidos
- **WHEN** un usuario con la cuenta bloqueada temporalmente restablece su contraseña
- **THEN** el sistema cambia la contraseña y la cuenta sigue bloqueada hasta la hora prevista
- **AND** la interfaz le indica a qué hora podrá iniciar sesión

#### Scenario: Acceso desactivado
- **WHEN** un usuario cuya identidad se desactivó después de recibir el enlace, o sin ninguna membresía activa en una clínica activa, restablece su contraseña
- **THEN** el sistema cambia la contraseña y no le da acceso
- **AND** la interfaz le indica que contacte al administrador de su clínica

#### Scenario: Membresía activa en una clínica suspendida
- **WHEN** un usuario cuya única membresía activa es de una clínica suspendida restablece su contraseña
- **THEN** el sistema cambia la contraseña y no le da acceso
- **AND** la interfaz le indica que contacte al administrador de su clínica

#### Scenario: Sin bloqueo vigente
- **WHEN** un usuario con intentos fallidos acumulados, o con un bloqueo ya vencido, restablece su contraseña
- **THEN** el contador de intentos fallidos vuelve a cero y no queda ningún bloqueo

#### Scenario: El estado solo se informa con un enlace válido
- **WHEN** alguien sin un enlace vigente intenta conocer el estado de una cuenta por esta vía
- **THEN** el sistema no revela si la cuenta existe, está bloqueada o desactivada

### Requirement: Recuperación excluida para administradores de plataforma

El sistema SHALL NOT enviar enlaces de recuperación a una cuenta de administrador de plataforma, aunque además tenga acceso a una clínica, y SHALL responder a su pedido lo mismo que a cualquier otro. Un enlace emitido antes de que la cuenta pasara a ser de plataforma SHALL rechazarse como inválido.

#### Scenario: Pedido de un administrador de plataforma
- **WHEN** alguien pide la recuperación con el correo de un administrador de plataforma
- **THEN** el sistema responde lo mismo que a cualquier otro correo
- **AND** no envía ningún correo

#### Scenario: Administrador de plataforma que también es miembro de una clínica
- **WHEN** alguien pide la recuperación con el correo de una cuenta que es administrador de plataforma y miembro de una clínica
- **THEN** el sistema no envía ningún correo

#### Scenario: La cuenta cambia de estado mientras se procesa el pedido
- **WHEN** una cuenta se desactiva o pasa a ser administrador de plataforma al mismo tiempo que se procesa su pedido de recuperación
- **THEN** el sistema no le envía un enlace que ya no le corresponde

#### Scenario: Enlace previo a pasar a plataforma
- **WHEN** alguien usa un enlace emitido antes de que la cuenta pasara a ser administrador de plataforma
- **THEN** el sistema lo rechaza con el mismo mensaje que un enlace vencido

### Requirement: Aviso por correo del cambio de contraseña

Cada vez que la contraseña de una cuenta cambia, al restablecerla con el enlace o al cambiarla desde el perfil propio, el sistema SHALL enviar un aviso a su correo con la fecha y hora del cambio y qué hacer si no fue el titular. El aviso SHALL NOT incluir la contraseña ni enlaces que la cambien. Si el aviso no se puede enviar, el cambio SHALL mantenerse.

#### Scenario: Aviso al restablecer
- **WHEN** un usuario restablece su contraseña con el enlace
- **THEN** recibe en su correo un aviso de que la contraseña se cambió, con la fecha y hora

#### Scenario: Aviso al cambiar desde Mi perfil
- **WHEN** un usuario cambia su contraseña desde Mi perfil
- **THEN** recibe en su correo el mismo aviso

#### Scenario: Qué hacer si no fue el titular
- **WHEN** el titular recibe el aviso de un cambio que no hizo
- **THEN** el aviso le indica que pida la recuperación de la contraseña y avise al administrador de su clínica

#### Scenario: Falla el envío del aviso
- **WHEN** el correo de aviso no se puede enviar
- **THEN** la contraseña queda cambiada igual
- **AND** el fallo queda registrado sin datos de la contraseña ni del enlace

#### Scenario: Un cambio rechazado no avisa
- **WHEN** un cambio o un restablecimiento de contraseña se rechaza
- **THEN** no se envía ningún aviso
