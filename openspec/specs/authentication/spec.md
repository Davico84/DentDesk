# authentication Specification

## Purpose

Gestiona la identidad y el acceso de los usuarios: registro de cuenta, inicio y cierre de sesión con credenciales, persistencia y renovación de la sesión mediante tokens, y protección de rutas según autenticación y rol.

## Requirements

### Requirement: Registro de usuario

El sistema SHALL permitir el registro únicamente mediante una invitación nominativa verificada; `POST /auth/register` SHALL rechazar solicitudes sin invitación válida y SHALL NOT crear organizaciones. La membresía SHALL tomar la organización y el rol de la invitación: un `ADMIN` de clínica solo invita `MEMBER` y la invitación `ADMIN` solo la emite un `PLATFORM_ADMIN`. SHALL iniciar sesión tras el alta y rechazar correos ya registrados o datos inválidos. El alta se rige por `multi-tenancy`.

#### Scenario: Registro exitoso mediante invitación
- **WHEN** un visitante presenta una invitación nominativa válida, verifica el correo invitado y envía su nombre completo y una contraseña válida cuya confirmación coincide
- **THEN** el sistema crea la identidad y la membresía con el rol autorizado por la invitación
- **AND** no crea una organización
- **AND** establece la sesión con la organización activa indicada por la invitación
- **AND** redirige al panel correspondiente a su rol

#### Scenario: Registro exitoso
- **WHEN** un visitante presenta una invitación nominativa válida, verifica el correo invitado y envía sus datos y una contraseña válida
- **THEN** el sistema crea la identidad y la membresía autorizada por la invitación
- **AND** establece la sesión con la organización indicada por la invitación

#### Scenario: Nombre de organización ausente (validación en cliente)
- **WHEN** un visitante abre el registro con una invitación válida
- **THEN** el frontend no solicita nombre de organización ni permite crear una clínica desde el formulario

#### Scenario: Contraseña demasiado corta (validación en cliente)
- **WHEN** un visitante escribe una contraseña de menos de 8 caracteres
- **THEN** el frontend muestra un error de validación
- **AND** no envía la petición al backend

#### Scenario: La confirmación de contraseña no coincide (validación en cliente)
- **WHEN** un visitante escribe una contraseña y una confirmación distintas
- **THEN** el frontend muestra un error en el campo de confirmación
- **AND** no envía la petición al backend

#### Scenario: Correo ya registrado
- **WHEN** un visitante intenta registrarse con un correo que ya existe en la plataforma
- **THEN** el sistema responde con un error de conflicto y un mensaje claro
- **AND** no crea una identidad duplicada ni una organización nueva
- **AND** no establece ninguna sesión

#### Scenario: Nombre de organización no solicitado
- **WHEN** un visitante abre el formulario de registro con una invitación válida
- **THEN** el frontend no solicita nombre de organización
- **AND** muestra la organización y el rol obtenidos de la invitación validada
- **AND** no permite seleccionar ni editar la organización

#### Scenario: Contraseña demasiado corta
- **WHEN** un visitante escribe una contraseña de menos de 8 caracteres
- **THEN** el frontend muestra un error de validación
- **AND** no envía la petición al backend

#### Scenario: La confirmación de contraseña no coincide
- **WHEN** un visitante escribe una contraseña y una confirmación distintas
- **THEN** el frontend muestra un error en el campo de confirmación
- **AND** no envía la petición al backend

#### Scenario: Registro sin invitación
- **WHEN** un visitante envía `/auth/register` sin una invitación válida
- **THEN** el sistema rechaza la operación
- **AND** no crea identidad, organización ni membresía

#### Scenario: Invitación inválida
- **WHEN** el token es inexistente, expirado, revocado, consumido o el correo verificado no coincide
- **THEN** el sistema devuelve un error uniforme
- **AND** no crea identidad, organización ni membresía

#### Scenario: ADMIN de clínica intenta conceder ADMIN
- **WHEN** un `ADMIN` de una clínica intenta emitir una invitación con rol `ADMIN`
- **THEN** el sistema rechaza la operación
- **AND** no crea la invitación

#### Scenario: Registro concurrente con la misma invitación
- **WHEN** dos solicitudes aceptan simultáneamente la misma invitación
- **THEN** exactamente una crea la identidad/membresía y consume la invitación
- **AND** la otra es rechazada

### Requirement: Inicio de sesión con credenciales
El sistema SHALL autenticar a un usuario por su identidad global (correo y contraseña), establecer su sesión al validarlas y rechazar credenciales inválidas sin revelar qué campo falló. El token de acceso SHALL identificar la organización activa y el rol del usuario en ella, según su membresía. SHALL conceder sesión solo si la identidad, la membresía y la organización están activas; si no, SHALL rechazar el acceso sin revelar el motivo. En este alcance cada usuario tiene una única membresía.

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

### Requirement: Bloqueo temporal del login por intentos fallidos
El sistema SHALL bloquear temporalmente una identidad tras un número configurable de intentos fallidos **consecutivos** y, mientras dure el bloqueo, SHALL rechazar el acceso aunque las credenciales sean correctas. Si se bloquea mientras otro login ya validó su contraseña sin establecer sesión, NO SHALL establecerla. El rechazo por bloqueo SHALL ser idéntico (estado, cuerpo y cabeceras) al de credenciales inválidas, sin revelar el bloqueo ni que el correo existe (no cubre la latencia).

#### Scenario: Bloqueo tras alcanzar el umbral de intentos fallidos
- **WHEN** un correo registrado acumula un número de intentos de contraseña fallidos consecutivos igual al umbral configurado
- **THEN** el sistema bloquea la identidad durante la ventana de tiempo configurada
- **AND** responde con el mensaje "Correo electrónico o contraseña incorrectos"
- **AND** no establece ninguna sesión

#### Scenario: Cuenta bloqueada con credenciales correctas
- **WHEN** un usuario cuya identidad está dentro de la ventana de bloqueo envía su correo y contraseña correctos
- **THEN** el sistema rechaza el acceso sin establecer sesión
- **AND** responde con el mismo mensaje "Correo electrónico o contraseña incorrectos" (no revela que la cuenta está bloqueada)

#### Scenario: Un bloqueo concurrente corta un login que ya validó la contraseña
- **WHEN** una identidad queda bloqueada por alcanzar el umbral de intentos fallidos justo mientras otra petición de login con credenciales correctas para esa misma identidad ya pasó la verificación de contraseña pero todavía no estableció la sesión
- **THEN** el sistema rechaza esa petición sin establecer sesión
- **AND** responde con el mismo mensaje genérico "Correo electrónico o contraseña incorrectos"

### Requirement: Conteo de intentos fallidos
El sistema SHALL incrementar el contador **solo** cuando la contraseña no coincide para un correo registrado. Un rechazo por estado (identidad, membresía u organización no activa) NO SHALL incrementarlo ni reiniciarlo. Un intento durante el bloqueo NO SHALL incrementarlo ni prolongar la ventana. Un correo no registrado NO SHALL crear ni alterar estado alguno. El incremento SHALL persistir aunque el intento se rechace y SHALL ser correcto con intentos concurrentes (N simultáneos cuentan N).

#### Scenario: El conteo persiste pese al rechazo del intento
- **WHEN** un intento de login se rechaza por contraseña incorrecta
- **THEN** el incremento del contador de esa identidad queda persistido
- **AND** un intento posterior parte de ese contador (los fallos acumulan hasta el umbral)

#### Scenario: Intentos fallidos concurrentes sobre la misma identidad
- **WHEN** varios intentos con contraseña incorrecta contra el mismo correo registrado llegan de forma simultánea
- **THEN** el contador refleja todos los intentos (no se pierde ninguno)
- **AND** al alcanzarse el umbral la identidad queda bloqueada

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

#### Scenario: Un intento contra un correo inexistente no crea estado de bloqueo
- **WHEN** un atacante envía repetidos intentos de login contra un correo que no está registrado
- **THEN** el sistema responde siempre con el mensaje "Correo electrónico o contraseña incorrectos"
- **AND** no crea ni altera ningún contador de intentos ni estado de bloqueo
- **AND** su respuesta (código de estado, cuerpo y cabeceras) es idéntica a la de un correo registrado que falla

### Requirement: Expiración y alcance del bloqueo
Un login exitoso SHALL reiniciar el contador. El bloqueo SHALL expirar solo al cumplirse una ventana configurable, sin desbloqueo manual, reiniciando el contador. Umbral y ventana SHALL ser configuración externalizada y validada. Bloquear SHALL impedir solo **nuevos** inicios de sesión, sin invalidar sesiones emitidas ni su renovación. El bloqueo SHALL ser independiente del estado administrativo de la identidad: expirar no la reactiva ni la altera, y el acceso SHALL exigir que ambos lo permitan.

#### Scenario: La ventana de bloqueo expira y el usuario acierta
- **WHEN** un usuario cuya identidad estuvo bloqueada envía credenciales correctas después de que la ventana de bloqueo haya transcurrido
- **THEN** el sistema establece la sesión normalmente
- **AND** reinicia el contador de intentos fallidos de la identidad

#### Scenario: La ventana de bloqueo expira y el usuario vuelve a fallar
- **WHEN** un usuario cuya identidad estuvo bloqueada envía una contraseña incorrecta después de que la ventana de bloqueo haya transcurrido
- **THEN** el sistema rechaza el intento con el mensaje genérico
- **AND** no vuelve a bloquear la identidad de inmediato (el conteo parte de cero tras la expiración)

#### Scenario: Expirar el bloqueo no reactiva una identidad deshabilitada
- **WHEN** una identidad deshabilitada o bloqueada administrativamente acumula intentos fallidos hasta quedar además bloqueada por tiempo, y esa ventana transcurre
- **THEN** el sistema sigue rechazando el acceso con el mensaje genérico, incluso con credenciales correctas
- **AND** su estado administrativo permanece intacto (expirar la ventana no la reactiva)

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

### Requirement: Secreto de firma de tokens obligatorio y robusto
El sistema SHALL negarse a arrancar si el secreto con el que firma los tokens (`app.security.jwt.secret`) no es seguro: ausente, vacío o en blanco, con un placeholder sin resolver (`${…}`), igual al valor de ejemplo versionado en `secrets.properties.example`, o de menos de 32 bytes en UTF-8. Ningún mensaje de error ni log de arranque SHALL revelar el valor del secreto. Los tests SHALL usar un secreto propio, con precedencia sobre la configuración local, el entorno y las propiedades de sistema.

#### Scenario: Secreto ausente
- **WHEN** la propiedad `app.security.jwt.secret` no tiene valor (no está definida)
- **THEN** la aplicación no arranca e indica que `app.security.jwt.secret` es obligatorio

#### Scenario: Placeholder sin resolver
- **WHEN** `JWT_SECRET` no está definida y el valor queda como el placeholder literal `${JWT_SECRET}`
- **THEN** la aplicación no arranca e indica que hay que definir `JWT_SECRET`
- **AND** lo mismo ocurre con cualquier otro placeholder sin resolver (p. ej. `${OTRA_VARIABLE}`)

#### Scenario: Secreto vacío o en blanco
- **WHEN** el secreto es la cadena vacía o solo tiene espacios
- **THEN** la aplicación no arranca e indica que `app.security.jwt.secret` es obligatorio

#### Scenario: Secreto demasiado corto
- **WHEN** el secreto tiene menos de 32 bytes en UTF-8 (p. ej. 31 bytes)
- **THEN** la aplicación no arranca e indica el mínimo de 32 bytes
- **AND** un secreto de exactamente 32 bytes sí es aceptado
- **AND** un secreto con menos de 32 caracteres pero al menos 32 bytes (caracteres multibyte) sí es aceptado

#### Scenario: Valor de ejemplo sin cambiar
- **WHEN** el secreto es el valor de `JWT_SECRET` de `secrets.properties.example`, también con espacios alrededor
- **THEN** la aplicación no arranca e indica que hay que generar un secreto propio

#### Scenario: El error no revela el secreto
- **WHEN** el arranque falla por un secreto inválido
- **THEN** ni la cadena de excepciones ni la salida del arranque contienen el valor del secreto

#### Scenario: Secreto válido
- **WHEN** el secreto tiene al menos 32 bytes y no es un placeholder ni el valor de ejemplo
- **THEN** la aplicación arranca y firma y verifica tokens con él

#### Scenario: Tests con secreto propio
- **WHEN** se ejecutan los tests de contexto completo, haya o no `secrets.properties` local, `JWT_SECRET` o `APP_SECURITY_JWT_SECRET` en el entorno, o las propiedades de sistema `JWT_SECRET` o `app.security.jwt.secret`
- **THEN** usan el secreto de test definido por la suite, no el placeholder literal ni ningún valor externo
