## MODIFIED Requirements

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
El sistema SHALL autenticar a un usuario por su identidad global (correo y contraseña), establecer su sesión al validarlas y rechazar credenciales inválidas sin revelar cuál campo falló. El token de acceso emitido SHALL identificar la organización activa y el rol del usuario en ella, tomados de su membresía, de modo que toda petición posterior quede acotada a esa organización. En este alcance cada usuario tiene una única membresía; el manejo de usuarios con varias membresías (selección de organización activa) se difiere a la capacidad `users`.

#### Scenario: Credenciales válidas
- **WHEN** un usuario registrado envía su correo y contraseña correctos
- **THEN** el sistema establece la sesión (token de acceso y de refresco)
- **AND** el token de acceso identifica la organización de su membresía y su rol en ella
- **AND** redirige al panel correspondiente a su rol

#### Scenario: Credenciales inválidas
- **WHEN** un usuario envía un correo inexistente o una contraseña incorrecta
- **THEN** el sistema responde con el mensaje "Correo electrónico o contraseña incorrectos"
- **AND** no establece ninguna sesión

#### Scenario: Campos vacíos
- **WHEN** un usuario intenta enviar el formulario de login con el correo o la contraseña vacíos
- **THEN** el frontend muestra errores de validación en los campos vacíos
- **AND** no envía la petición al backend

### Requirement: Renovación de token
El sistema SHALL renovar de forma transparente el token de acceso expirado usando el token de refresco, y cerrar la sesión cuando el refresco ya no sea válido. El token de acceso renovado SHALL conservar la organización activa de la sesión y reflejar el rol vigente del usuario en su membresía con esa organización.

#### Scenario: Token de acceso expirado se renueva y reintenta
- **WHEN** una petición autenticada recibe `401` por token de acceso expirado y existe un token de refresco válido
- **THEN** el sistema solicita un nuevo token de acceso mediante el endpoint de refresco
- **AND** el token renovado conserva la misma organización activa y refleja el rol vigente de la membresía
- **AND** reintenta la petición original de forma transparente para el usuario

#### Scenario: Refresco inválido o expirado
- **WHEN** el intento de renovación falla porque el token de refresco es inválido o expiró
- **THEN** el sistema cierra la sesión
- **AND** redirige al usuario a la pantalla de login

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
