# Spec Delta — project-foundation

## ADDED Requirements

### Requirement: Contacto de soporte en las pantallas de acceso
Las pantallas sin sesión (login, registro y la espera de arranque en frío) SHALL mostrar el contacto de soporte de Dentdesk: "¿Necesitas ayuda?" con un enlace a WhatsApp, que se abre en otra pestaña con un mensaje inicial que nombra el producto, y un enlace al correo. SHALL verse en escritorio y en celular sin repetirse en la misma pantalla. La espera larga del arranque en frío SHALL ofrecer ese contacto en vez de remitir al administrador.

#### Scenario: Contacto en el panel de marca
- **WHEN** se abre el login en escritorio
- **THEN** el panel de marca muestra "¿Necesitas ayuda?"
- **AND** un enlace "Escríbenos por WhatsApp" a `https://wa.me/51959396384` con el mensaje inicial, que se abre en otra pestaña con `rel="noopener noreferrer"` y lo avisa en su nombre accesible
- **AND** un enlace `mailto:` con el correo de soporte

#### Scenario: Contacto en celular sin repetir
- **WHEN** se abre el login o el registro en celular
- **THEN** el contacto aparece debajo del formulario
- **AND** en escritorio ese bloque bajo el formulario no se muestra, porque ya está en el panel

#### Scenario: Contacto en la espera larga
- **WHEN** la pantalla de arranque llega a "Está tardando más de lo normal"
- **THEN** muestra "Si el problema continúa, escríbenos:" con los enlaces de contacto
- **AND** ya no remite al administrador

#### Scenario: Sin red no se ofrece el contacto
- **WHEN** la pantalla de arranque muestra "Sin conexión a internet"
- **THEN** no muestra los enlaces de contacto bajo el mensaje, porque sin red no se pueden abrir
