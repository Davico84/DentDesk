## MODIFIED Requirements

### Requirement: Registro de usuario

El sistema SHALL permitir completar el registro únicamente mediante una invitación nominativa verificada. `POST /auth/register` SHALL rechazar solicitudes sin invitación válida y SHALL nunca crear una organización. Una invitación emitida por un `ADMIN` de clínica SHALL crear una membresía `MEMBER`; una invitación con rol `ADMIN` SHALL provenir únicamente de `PLATFORM_ADMIN`, definido en el change `add-platform-admin`. SHALL iniciar sesión automáticamente tras el alta y SHALL rechazar correos duplicados o datos que no cumplan las validaciones mínimas. El correo SHALL ser único en toda la plataforma. El alta se rige por `multi-tenancy`.

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

