## ADDED Requirements

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

#### Scenario: Un cambio fallido no cierra nada
- **WHEN** el cambio de contraseña se rechaza por cualquier motivo
- **THEN** ninguna sesión del usuario se cierra
