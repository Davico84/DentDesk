# Spec Delta — project-foundation

## ADDED Requirements

### Requirement: Contacto de soporte en las pantallas de acceso
El login y el registro SHALL mostrar bajo el formulario el contacto de soporte de Dentdesk en una fila compacta: la pregunta "¿Problemas para entrar?" y dos botones de solo ícono, WhatsApp (en otra pestaña, con un mensaje inicial que nombra el producto) y correo, cada uno con nombre accesible y tooltip. La espera larga del arranque en frío SHALL mostrar esa fila en vez de remitir al administrador.

#### Scenario: Contacto en el login y el registro
- **WHEN** se abre el login o el registro, en escritorio o en celular
- **THEN** bajo el formulario se ve "¿Problemas para entrar?" con los botones de WhatsApp y de correo

#### Scenario: Botones del contacto
- **WHEN** la fila de soporte está a la vista
- **THEN** el botón de WhatsApp enlaza a `https://wa.me/51959396384` con el mensaje inicial, se abre en otra pestaña con `rel="noopener noreferrer"` y lo avisa en su nombre accesible
- **AND** el de correo es un enlace `mailto:` al correo de soporte, con la dirección en su nombre accesible
- **AND** ninguno usa el atributo `title`

#### Scenario: Tooltip de los botones
- **WHEN** se pasa el ratón sobre un botón de la fila o se lo enfoca con el teclado
- **THEN** un tooltip muestra a qué lleva (WhatsApp, o el correo)

#### Scenario: Contacto en la espera larga
- **WHEN** la pantalla de arranque llega a "Está tardando más de lo normal"
- **THEN** muestra la fila de soporte bajo "Reintentar", con "¿Sigue sin entrar?"
- **AND** ya no remite al administrador

#### Scenario: Sin red no se ofrece el contacto
- **WHEN** la pantalla de arranque muestra "Sin conexión a internet"
- **THEN** no muestra la fila de soporte, porque sin red no se pueden abrir sus enlaces
