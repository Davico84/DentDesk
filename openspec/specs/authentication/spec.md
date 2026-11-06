# authentication Specification

## Purpose

Gestiona la identidad y el acceso de los usuarios: registro de cuenta, inicio y cierre de sesión con credenciales, persistencia y renovación de la sesión mediante tokens, y protección de rutas según autenticación y rol, y el perfil propio: corregir el nombre y cambiar la contraseña, cerrando las otras sesiones.

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

### Requirement: Ver el perfil propio

El sistema SHALL permitir a cada usuario autenticado ver su propio perfil: nombre completo y correo. El perfil SHALL ser siempre el del usuario de la sesión: ninguna petición SHALL leer el perfil de otro usuario por esta vía. La sesión de un administrador de plataforma SHALL NOT tener perfil de clínica.

#### Scenario: Ver el perfil
- **WHEN** un usuario autenticado abre Mi perfil
- **THEN** el sistema muestra su nombre completo y su correo
- **AND** el correo se muestra como dato de solo lectura

#### Scenario: El perfil es siempre el propio
- **WHEN** una petición al perfil propio intenta indicar otro usuario, aun por API directa y aun siendo `ADMIN`
- **THEN** el sistema responde con el perfil del usuario de la sesión y nunca con el de otro

#### Scenario: Sin sesión
- **WHEN** una petición sin sesión llega al perfil propio
- **THEN** el sistema la rechaza como no autenticada

#### Scenario: Sesión de plataforma
- **WHEN** una petición con la sesión de un administrador de plataforma llega al perfil propio
- **THEN** el sistema la rechaza como no autenticada

### Requirement: Corregir el nombre propio

El sistema SHALL permitir a cada usuario corregir su propio nombre completo: entre 2 y 120 caracteres, sin contar los espacios de los extremos, que no se guardan. El correo SHALL NOT poder cambiarse por esta vía. Corregir el nombre SHALL NOT alterar el nombre copiado en los actos clínicos ya firmados.

#### Scenario: Corregir el nombre
- **WHEN** un usuario guarda un nombre completo válido
- **THEN** el sistema lo actualiza
- **AND** la interfaz muestra el nombre nuevo, también en el menú de la cuenta, sin volver a iniciar sesión

#### Scenario: Nombre demasiado corto o largo
- **WHEN** un usuario guarda un nombre de menos de 2 o de más de 120 caracteres, o solo con espacios
- **THEN** el formulario muestra el error y no envía la petición
- **AND** el servidor también lo rechaza si la petición llega directo

#### Scenario: Intentar cambiar el correo
- **WHEN** una petición para corregir el perfil incluye un correo distinto
- **THEN** el sistema no cambia el correo

#### Scenario: Lo ya firmado conserva el nombre de entonces
- **WHEN** un usuario que firmó una nota corrige su nombre
- **THEN** la nota firmada sigue mostrando el nombre con el que se firmó
- **AND** las firmas nuevas llevan el nombre corregido

### Requirement: Cambiar la contraseña propia

El sistema SHALL permitir a cada usuario cambiar su contraseña indicando la actual. La nueva SHALL tener al menos 8 caracteres, como en el registro, y ser distinta de la actual. Una contraseña actual incorrecta SHALL rechazarse sin cambiar nada y SHALL contar como intento fallido para el bloqueo temporal del login; con la cuenta bloqueada, el cambio SHALL rechazarse.

#### Scenario: Cambiar la contraseña
- **WHEN** un usuario indica su contraseña actual correcta y una nueva válida, confirmada
- **THEN** el sistema cambia la contraseña
- **AND** el login siguiente funciona con la nueva y no con la anterior

#### Scenario: Contraseña actual incorrecta
- **WHEN** un usuario indica una contraseña actual incorrecta
- **THEN** el sistema rechaza el cambio, indica que la contraseña actual no es correcta y no cambia nada
- **AND** el intento cuenta como fallido para el bloqueo temporal del login

#### Scenario: Adivinar la contraseña con una sesión abierta
- **WHEN** desde una sesión abierta se prueban contraseñas actuales incorrectas hasta alcanzar el límite de intentos
- **THEN** la cuenta queda bloqueada temporalmente
- **AND** mientras dure el bloqueo, el cambio se rechaza aun con la contraseña actual correcta

#### Scenario: Un cambio exitoso reinicia los intentos fallidos
- **WHEN** un usuario con intentos fallidos acumulados, o con un bloqueo ya vencido, cambia su contraseña
- **THEN** el contador de intentos fallidos vuelve a cero y no queda ningún bloqueo

#### Scenario: Nueva contraseña demasiado corta
- **WHEN** un usuario indica una contraseña nueva de menos de 8 caracteres
- **THEN** el formulario muestra el error y no envía la petición
- **AND** el servidor también lo rechaza si la petición llega directo

#### Scenario: La confirmación no coincide
- **WHEN** la confirmación de la contraseña nueva no coincide con la nueva
- **THEN** el formulario muestra el error y no envía la petición

#### Scenario: La nueva es igual a la actual
- **WHEN** un usuario indica como nueva la misma contraseña actual
- **THEN** el sistema rechaza el cambio e indica que debe ser distinta

#### Scenario: Sesión de plataforma
- **WHEN** una petición con la sesión de un administrador de plataforma intenta cambiar la contraseña por esta vía
- **THEN** el sistema la rechaza como no autenticada

### Requirement: Cerrar las otras sesiones al cambiar la contraseña

Al cambiar la contraseña, el sistema SHALL cerrar todas las demás sesiones del usuario, en todos los dispositivos y clínicas, y SHALL mantener abierta la sesión desde la que se hizo el cambio. Una sesión cerrada SHALL NOT poder renovarse; su acceso vigente SHALL terminar, a más tardar, cuando venza su token de acceso.

#### Scenario: Otra sesión abierta queda cerrada
- **WHEN** un usuario con una sesión abierta en otro dispositivo cambia su contraseña
- **THEN** la otra sesión no puede renovarse y vuelve a la pantalla de login al vencer su acceso

#### Scenario: La sesión actual sigue abierta
- **WHEN** un usuario cambia su contraseña
- **THEN** sigue trabajando en la misma sesión sin volver a iniciar sesión
- **AND** al recargar la aplicación la sesión se restaura

#### Scenario: Una sesión que se inicia o renueva durante el cambio también se cierra
- **WHEN** otra sesión del usuario se renueva, o alguien inicia sesión con la contraseña anterior, al mismo tiempo que el cambio de contraseña
- **THEN** esa sesión tampoco puede renovarse después del cambio, o el inicio de sesión se rechaza

#### Scenario: La sesión actual sigue en su clínica
- **WHEN** un usuario con acceso a más de una clínica cambia su contraseña desde una de ellas
- **THEN** la sesión actual sigue en la misma clínica
- **AND** las sesiones abiertas en las otras clínicas quedan cerradas

#### Scenario: Un cambio fallido no cierra nada
- **WHEN** el cambio de contraseña se rechaza por cualquier motivo
- **THEN** ninguna sesión del usuario se cierra

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
- **THEN** las cinco respuestas son idénticas en status, cuerpo y headers, salvo los que cambian en cada petición sin depender de la cuenta (la fecha y el identificador de traza)
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

### Requirement: Admisión de los pedidos de recuperación

El sistema SHALL limitar los pedidos de recuperación por IP del cliente y SHALL atender un solo pedido pendiente por correo, sin consultar la cuenta y respondiendo lo mismo que a cualquier otro pedido. Un pedido que no se atiende por el límite, por estar repetido o por saturación SHALL NOT enviar correo. Los avisos de cambio de contraseña SHALL NOT depender de la carga de pedidos de recuperación.

#### Scenario: Muchos pedidos desde la misma IP
- **WHEN** una misma IP del cliente supera el límite de pedidos de recuperación
- **THEN** el sistema responde lo mismo que a cualquier otro pedido
- **AND** los pedidos que exceden el límite no envían correo

#### Scenario: Pedidos simultáneos desde la misma IP
- **WHEN** desde una misma IP llegan a la vez más pedidos que el límite
- **THEN** se atienden como máximo tantos como el límite

#### Scenario: Una IP declarada por el cliente no cuenta
- **WHEN** un pedido declara otra IP en cabeceras que el cliente controla
- **THEN** el límite se aplica a la IP del cliente que informa el proxy de confianza

#### Scenario: Pedido repetido mientras otro está pendiente
- **WHEN** se pide la recuperación para un correo que tiene otro pedido aún sin procesar
- **THEN** el sistema responde lo mismo que a cualquier otro pedido
- **AND** los dos pedidos generan como máximo un enlace

#### Scenario: Los avisos no esperan a los pedidos
- **WHEN** los pedidos de recuperación tienen saturado el sistema y una cuenta cambia su contraseña
- **THEN** el aviso de cambio de contraseña se envía igual

#### Scenario: Un pedido no atendido
- **WHEN** un pedido de recuperación no se atiende
- **THEN** la pantalla de pedido ya le había indicado que, si el enlace no llega en unos minutos, vuelva a pedirlo

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
