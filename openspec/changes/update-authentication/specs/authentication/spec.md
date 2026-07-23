## MODIFIED Requirements

### Requirement: Inicio de sesión con credenciales
El sistema SHALL autenticar a un usuario por su identidad global (correo y contraseña), establecer su sesión al validarlas y rechazar credenciales inválidas sin revelar cuál campo falló. El token de acceso emitido SHALL identificar la organización activa y el rol del usuario en ella, tomados de su membresía, de modo que toda petición posterior quede acotada a esa organización. El sistema SHALL conceder sesión únicamente cuando la identidad, la membresía y la organización estén activas; en caso contrario SHALL rechazar el acceso sin revelar el motivo. En este alcance cada usuario tiene una única membresía; el manejo de usuarios con varias membresías (selección de organización activa) se difiere a la capacidad `users`.

El sistema SHALL proteger el login contra fuerza bruta bloqueando temporalmente la identidad tras un número configurable de intentos fallidos **consecutivos** contra un correo registrado. Mientras la identidad esté bloqueada, el sistema SHALL rechazar el acceso aunque las credenciales sean correctas, sin emitir sesión.

Sobre qué se contabiliza: SHALL incrementar el contador **únicamente** cuando la contraseña no coincide para un correo registrado. Un rechazo por **estado** (identidad, membresía u organización no activa) con contraseña correcta NO SHALL incrementar el contador. Un intento realizado mientras la identidad ya está bloqueada NO SHALL incrementar el contador ni prolongar la ventana de bloqueo. Un intento contra un correo no registrado NO SHALL crear ni alterar estado alguno.

Sobre la persistencia del conteo: el incremento SHALL persistir aunque el intento se rechace con error, y SHALL ser correcto cuando varios intentos concurren sobre la misma identidad (N intentos fallidos simultáneos cuentan N).

Sobre el ciclo de vida del bloqueo: un inicio de sesión exitoso SHALL reiniciar el contador. El bloqueo SHALL expirar automáticamente al transcurrir una ventana de tiempo configurable, sin desbloqueo manual en este alcance. Al expirar la ventana, el contador SHALL reiniciarse, de modo que un único intento fallido posterior no vuelva a bloquear de inmediato. El umbral de intentos y la duración de la ventana SHALL ser configuración externalizada y validada (no valores hardcodeados).

Sobre lo que se revela: la respuesta de un login rechazado por bloqueo SHALL ser idéntica —en código de estado, cuerpo y cabeceras— a la de unas credenciales inválidas; el sistema no SHALL revelar que la cuenta está bloqueada ni que el correo existe. Esta garantía cubre la **respuesta**, no la latencia (la indistinguibilidad temporal queda fuera de este alcance).

Sobre el alcance del bloqueo: bloquear una identidad SHALL impedir **nuevos** inicios de sesión, y NO SHALL invalidar las sesiones ya emitidas ni afectar a la renovación de token de una sesión vigente.

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

#### Scenario: Bloquear no cierra las sesiones ya abiertas
- **WHEN** una identidad queda bloqueada por intentos fallidos mientras tiene una sesión vigente en otro dispositivo
- **THEN** esa sesión sigue funcionando y su token de acceso se renueva con normalidad
- **AND** el bloqueo solo impide iniciar sesión de nuevo con credenciales
