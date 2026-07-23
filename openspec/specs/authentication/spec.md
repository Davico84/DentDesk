# authentication Specification

## Purpose

Gestiona la identidad y el acceso de los usuarios: registro de cuenta, inicio y cierre de sesión con credenciales, persistencia y renovación de la sesión mediante tokens, y protección de rutas según autenticación y rol.

## Requirements

### Requirement: Registro de usuario
El sistema SHALL permitir crear una organización junto con su configuración por defecto, la identidad de su primer usuario y una membresía de administrador que los una, a partir del nombre de la organización, el nombre completo, el correo y la contraseña (con confirmación de contraseña en el cliente); SHALL iniciar sesión automáticamente tras el alta; y SHALL rechazar correos duplicados o datos que no cumplan las validaciones mínimas. La membresía creada SHALL tener rol `ADMIN` en la organización recién creada. El correo SHALL ser único en toda la plataforma. Otros datos de perfil (nombre de usuario, teléfono, país) se capturan más adelante en la gestión de perfil (capacidad `users`). El alta de la organización, la identidad y la membresía se rige por la capacidad `multi-tenancy`.

#### Scenario: Registro exitoso
- **WHEN** un visitante envía el formulario con el nombre de la organización, su nombre completo, un correo válido y una contraseña de al menos 8 caracteres cuya confirmación coincide, con un correo no registrado
- **THEN** el sistema crea la organización, la identidad del usuario y una membresía con rol `ADMIN`
- **AND** establece la sesión (emite token de acceso —cuya organización activa es la recién creada— y de refresco)
- **AND** redirige al panel correspondiente a su rol

#### Scenario: Correo ya registrado
- **WHEN** un visitante intenta registrarse con un correo que ya existe en la plataforma, pertenezca a la organización que pertenezca
- **THEN** el sistema responde con un error de conflicto y un mensaje claro
- **AND** no crea una identidad duplicada ni una organización nueva
- **AND** no establece ninguna sesión

#### Scenario: Nombre de organización ausente (validación en cliente)
- **WHEN** un visitante intenta enviar el formulario de registro sin indicar el nombre de la organización
- **THEN** el frontend muestra un error de validación en el campo
- **AND** no envía la petición al backend

#### Scenario: Contraseña demasiado corta (validación en cliente)
- **WHEN** un visitante escribe una contraseña de menos de 8 caracteres en el formulario de registro
- **THEN** el frontend muestra un error de validación en el campo
- **AND** no envía la petición al backend

#### Scenario: La confirmación de contraseña no coincide (validación en cliente)
- **WHEN** un visitante escribe una contraseña y una confirmación distintas
- **THEN** el frontend muestra un error de validación en el campo de confirmación
- **AND** no envía la petición al backend

### Requirement: Inicio de sesión con credenciales
El sistema SHALL autenticar a un usuario por su identidad global (correo y contraseña), establecer su sesión al validarlas y rechazar credenciales inválidas sin revelar cuál campo falló. El token de acceso emitido SHALL identificar la organización activa y el rol del usuario en ella, tomados de su membresía, de modo que toda petición posterior quede acotada a esa organización. El sistema SHALL conceder sesión únicamente cuando la identidad, la membresía y la organización estén activas; en caso contrario SHALL rechazar el acceso sin revelar el motivo. En este alcance cada usuario tiene una única membresía; el manejo de usuarios con varias membresías (selección de organización activa) se difiere a la capacidad `users`.

El sistema SHALL proteger el login contra fuerza bruta bloqueando temporalmente la identidad tras un número configurable de intentos fallidos **consecutivos** contra un correo registrado. Mientras la identidad esté bloqueada, el sistema SHALL rechazar el acceso aunque las credenciales sean correctas, sin emitir sesión.

Sobre qué se contabiliza: SHALL incrementar el contador **únicamente** cuando la contraseña no coincide para un correo registrado. Un rechazo por **estado** (identidad, membresía u organización no activa) con contraseña correcta NO SHALL incrementar **ni reiniciar** el contador: un intento que no llega a establecer sesión SHALL dejar el estado de bloqueo como estaba. Un intento realizado mientras la identidad ya está bloqueada NO SHALL incrementar el contador ni prolongar la ventana de bloqueo. Un intento contra un correo no registrado NO SHALL crear ni alterar estado alguno.

Sobre la persistencia del conteo: el incremento SHALL persistir aunque el intento se rechace con error, y SHALL ser correcto cuando varios intentos concurren sobre la misma identidad (N intentos fallidos simultáneos cuentan N).

Sobre el ciclo de vida del bloqueo: un inicio de sesión exitoso SHALL reiniciar el contador. El bloqueo SHALL expirar automáticamente al transcurrir una ventana de tiempo configurable, sin desbloqueo manual en este alcance. Al expirar la ventana, el contador SHALL reiniciarse, de modo que un único intento fallido posterior no vuelva a bloquear de inmediato. El umbral de intentos y la duración de la ventana SHALL ser configuración externalizada y validada (no valores hardcodeados).

Sobre lo que se revela: la respuesta de un login rechazado por bloqueo SHALL ser idéntica —en código de estado, cuerpo y cabeceras— a la de unas credenciales inválidas; el sistema no SHALL revelar que la cuenta está bloqueada ni que el correo existe. Esta garantía cubre la **respuesta**, no la latencia (la indistinguibilidad temporal queda fuera de este alcance).

Sobre el alcance del bloqueo: bloquear una identidad SHALL impedir **nuevos** inicios de sesión, y NO SHALL invalidar las sesiones ya emitidas ni afectar a la renovación de token de una sesión vigente.

Sobre bloqueos que ocurren durante un login en curso: si una identidad queda bloqueada **mientras** una petición de login concurrente ya validó sus credenciales pero aún no estableció la sesión, el sistema NO SHALL establecer esa sesión — SHALL rechazarla con el mismo mensaje genérico, como si el bloqueo hubiera existido desde el inicio de la petición.

Sobre su relación con el estado de la identidad: el bloqueo temporal por intentos fallidos SHALL ser un mecanismo **independiente** del estado administrativo de la identidad. Expirar la ventana de bloqueo NO SHALL reactivar una identidad que esté deshabilitada o bloqueada administrativamente, ni alterar su estado de ninguna forma. Ambos controles SHALL evaluarse por separado y el acceso SHALL concederse solo si los dos lo permiten.

#### Scenario: Credenciales válidas
- **WHEN** un usuario registrado envía su correo y contraseña correctos y su identidad no está bloqueada
- **THEN** el sistema establece la sesión (token de acceso y de refresco)
- **AND** el token de acceso identifica la organización de su membresía y su rol en ella
- **AND** reinicia el contador de intentos fallidos de la identidad
- **AND** redirige al panel correspondiente a su rol

#### Scenario: Credenciales inválidas
- **WHEN** un usuario envía un correo inexistente o una contraseña incorrecta
- **THEN** el sistema responde con el mensaje "Correo electrónico o contraseña incorrectos"
- **AND** no establece ninguna sesión

#### Scenario: Campos vacíos
- **WHEN** un usuario intenta enviar el formulario de login con el correo o la contraseña vacíos
- **THEN** el frontend muestra errores de validación en los campos vacíos
- **AND** no envía la petición al backend

#### Scenario: Identidad o membresía no activa
- **WHEN** un usuario con credenciales correctas tiene su identidad deshabilitada, su membresía inactiva o su organización suspendida
- **THEN** el sistema rechaza el acceso con el mismo mensaje que unas credenciales inválidas
- **AND** no establece ninguna sesión
- **AND** no revela cuál de los tres estados provocó el rechazo

#### Scenario: Bloqueo tras alcanzar el umbral de intentos fallidos
- **WHEN** un correo registrado acumula un número de intentos de contraseña fallidos consecutivos igual al umbral configurado
- **THEN** el sistema bloquea la identidad durante la ventana de tiempo configurada
- **AND** responde con el mensaje "Correo electrónico o contraseña incorrectos"
- **AND** no establece ninguna sesión

#### Scenario: El conteo persiste pese al rechazo del intento
- **WHEN** un intento de login se rechaza por contraseña incorrecta
- **THEN** el incremento del contador de esa identidad queda persistido
- **AND** un intento posterior parte de ese contador (los fallos acumulan hasta el umbral)

#### Scenario: Intentos fallidos concurrentes sobre la misma identidad
- **WHEN** varios intentos con contraseña incorrecta contra el mismo correo registrado llegan de forma simultánea
- **THEN** el contador refleja todos los intentos (no se pierde ninguno)
- **AND** al alcanzarse el umbral la identidad queda bloqueada

#### Scenario: Cuenta bloqueada con credenciales correctas
- **WHEN** un usuario cuya identidad está dentro de la ventana de bloqueo envía su correo y contraseña correctos
- **THEN** el sistema rechaza el acceso sin establecer sesión
- **AND** responde con el mismo mensaje "Correo electrónico o contraseña incorrectos" (no revela que la cuenta está bloqueada)

#### Scenario: Insistir durante el bloqueo no prolonga la ventana
- **WHEN** un usuario cuya identidad está bloqueada realiza más intentos antes de que expire la ventana
- **THEN** el sistema los rechaza con el mensaje genérico
- **AND** no incrementa el contador ni retrasa el momento de expiración del bloqueo

#### Scenario: Un rechazo por estado no cuenta para el bloqueo
- **WHEN** un usuario con contraseña correcta es rechazado porque su identidad, membresía u organización no está activa
- **THEN** el sistema no incrementa su contador de intentos fallidos
- **AND** repetir el intento no llega a bloquear la identidad

#### Scenario: Un rechazo por estado tampoco borra los intentos ya acumulados
- **WHEN** un usuario con intentos fallidos ya acumulados envía la contraseña correcta pero es rechazado porque su identidad, membresía u organización no está activa
- **THEN** su contador de intentos fallidos permanece intacto
- **AND** los intentos acumulados siguen contando para un bloqueo posterior

#### Scenario: La ventana de bloqueo expira y el usuario acierta
- **WHEN** un usuario cuya identidad estuvo bloqueada envía credenciales correctas después de que la ventana de bloqueo haya transcurrido
- **THEN** el sistema establece la sesión normalmente
- **AND** reinicia el contador de intentos fallidos de la identidad

#### Scenario: La ventana de bloqueo expira y el usuario vuelve a fallar
- **WHEN** un usuario cuya identidad estuvo bloqueada envía una contraseña incorrecta después de que la ventana de bloqueo haya transcurrido
- **THEN** el sistema rechaza el intento con el mensaje genérico
- **AND** no vuelve a bloquear la identidad de inmediato (el conteo parte de cero tras la expiración)

#### Scenario: Un intento contra un correo inexistente no crea estado de bloqueo
- **WHEN** un atacante envía repetidos intentos de login contra un correo que no está registrado
- **THEN** el sistema responde siempre con el mensaje "Correo electrónico o contraseña incorrectos"
- **AND** no crea ni altera ningún contador de intentos ni estado de bloqueo
- **AND** su respuesta (código de estado, cuerpo y cabeceras) es idéntica a la de un correo registrado que falla

#### Scenario: Expirar el bloqueo no reactiva una identidad deshabilitada
- **WHEN** una identidad deshabilitada o bloqueada administrativamente acumula intentos fallidos hasta quedar además bloqueada por tiempo, y esa ventana transcurre
- **THEN** el sistema sigue rechazando el acceso con el mensaje genérico, incluso con credenciales correctas
- **AND** su estado administrativo permanece intacto (expirar la ventana no la reactiva)

#### Scenario: Un bloqueo concurrente corta un login que ya validó la contraseña
- **WHEN** una identidad queda bloqueada por alcanzar el umbral de intentos fallidos justo mientras otra petición de login con credenciales correctas para esa misma identidad ya pasó la verificación de contraseña pero todavía no estableció la sesión
- **THEN** el sistema rechaza esa petición sin establecer sesión
- **AND** responde con el mismo mensaje genérico "Correo electrónico o contraseña incorrectos"

#### Scenario: Bloquear no cierra las sesiones ya abiertas
- **WHEN** una identidad queda bloqueada por intentos fallidos mientras tiene una sesión vigente en otro dispositivo
- **THEN** esa sesión sigue funcionando y su token de acceso se renueva con normalidad
- **AND** el bloqueo solo impide iniciar sesión de nuevo con credenciales

### Requirement: Persistencia de sesión
El sistema SHALL restaurar la sesión del usuario tras recargar la aplicación cuando la sesión siga vigente (token de refresco válido), y presentarse como no autenticado cuando no exista una sesión restaurable.

#### Scenario: Recarga con sesión vigente
- **WHEN** el usuario recarga la aplicación teniendo una sesión vigente (token de refresco válido)
- **THEN** el sistema restaura la sesión sin volver a pedir credenciales
- **AND** el usuario permanece en la ruta solicitada según su rol

#### Scenario: Sin sesión restaurable
- **WHEN** el usuario abre la aplicación sin sesión previa o con el refresco ausente/expirado
- **THEN** el sistema queda en estado no autenticado
- **AND** limpia cualquier dato de sesión residual del almacenamiento

### Requirement: Renovación de token
El sistema SHALL renovar de forma transparente el token de acceso expirado usando el token de refresco, y cerrar la sesión cuando el refresco ya no sea válido. El token de acceso renovado SHALL conservar la organización activa de la sesión y reflejar el rol vigente del usuario en su membresía con esa organización. La renovación SHALL reevaluar que la identidad, la membresía y la organización sigan activas, y cerrar la sesión si alguna dejó de estarlo.

#### Scenario: Token de acceso expirado se renueva y reintenta
- **WHEN** una petición autenticada recibe `401` por token de acceso expirado y existe un token de refresco válido
- **THEN** el sistema solicita un nuevo token de acceso mediante el endpoint de refresco
- **AND** el token renovado conserva la misma organización activa y refleja el rol vigente de la membresía
- **AND** reintenta la petición original de forma transparente para el usuario

#### Scenario: Refresco inválido o expirado
- **WHEN** el intento de renovación falla porque el token de refresco es inválido o expiró
- **THEN** el sistema cierra la sesión
- **AND** redirige al usuario a la pantalla de login

#### Scenario: La membresía deja de estar activa durante la sesión
- **WHEN** una renovación ocurre después de que la membresía del usuario haya sido suspendida o su organización desactivada
- **THEN** el sistema no emite un nuevo token de acceso
- **AND** cierra la sesión y redirige a la pantalla de login

### Requirement: Cierre de sesión
El sistema SHALL permitir al usuario cerrar su sesión de forma explícita, invalidando la sesión y eliminando los datos locales.

#### Scenario: Logout manual
- **WHEN** un usuario autenticado ejecuta la acción de cerrar sesión
- **THEN** el sistema invalida la sesión y elimina los tokens y los datos del usuario del almacenamiento local
- **AND** redirige a la pantalla de login

### Requirement: Protección de rutas por autenticación y rol
El sistema SHALL controlar el acceso a las rutas según el estado de autenticación y el rol del usuario, redirigiendo o denegando el acceso según corresponda. El rol SHALL interpretarse dentro de la organización activa del usuario (el de su membresía): la autorización nunca concede acceso a datos de otra organización, con independencia del rol.

#### Scenario: Acceso sin sesión a ruta privada
- **WHEN** un visitante sin sesión intenta acceder a una ruta privada
- **THEN** el sistema lo redirige a la pantalla de login

#### Scenario: Acceso con sesión a rutas de autenticación
- **WHEN** un usuario autenticado intenta acceder a `/auth` (login o registro)
- **THEN** el sistema lo redirige a su panel

#### Scenario: Rol sin permiso
- **WHEN** un usuario autenticado intenta acceder a una ruta para la que su rol en la organización activa no tiene permiso
- **THEN** el sistema muestra un estado de acceso denegado con la opción de volver
- **AND** no renderiza el contenido protegido

#### Scenario: El rol no traspasa la frontera de la organización
- **WHEN** un usuario con rol `ADMIN` en la organización A solicita un recurso protegido perteneciente a la organización B
- **THEN** el sistema deniega el acceso
- **AND** no revela ningún dato de la organización B
