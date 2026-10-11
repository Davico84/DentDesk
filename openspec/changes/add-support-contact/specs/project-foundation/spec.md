# Spec Delta — project-foundation

## ADDED Requirements

### Requirement: Contacto de soporte en las pantallas de acceso
El login y el registro SHALL ofrecer, bajo el formulario, un botón "¿Problemas para entrar? Habla con soporte" que despliega la tarjeta de soporte de Dentdesk: quién atiende, en qué horario, un botón de WhatsApp (en otra pestaña, con un mensaje inicial que nombra el producto) y uno de correo. La espera larga del arranque en frío SHALL mostrar esa tarjeta sin pulsar nada, en vez de remitir al administrador.

#### Scenario: Desplegar el contacto en el login
- **WHEN** en el login o el registro se pulsa "¿Problemas para entrar? Habla con soporte"
- **THEN** aparece la tarjeta con "David Velarde · Soporte Dentdesk" y el horario "de 9:00 a 20:00"
- **AND** el botón lo refleja con `aria-expanded="true"`

#### Scenario: Enlaces de la tarjeta
- **WHEN** la tarjeta de soporte está a la vista
- **THEN** el botón de WhatsApp enlaza a `https://wa.me/51959396384` con el mensaje inicial, se abre en otra pestaña con `rel="noopener noreferrer"` y lo avisa en su nombre accesible
- **AND** el de correo es un enlace `mailto:` al correo de soporte

#### Scenario: Ocultar el contacto
- **WHEN** con la tarjeta desplegada se vuelve a pulsar el botón
- **THEN** la tarjeta se oculta y el botón vuelve a `aria-expanded="false"`

#### Scenario: Contacto en la espera larga
- **WHEN** la pantalla de arranque llega a "Está tardando más de lo normal"
- **THEN** muestra la tarjeta de soporte bajo "Reintentar", sin pulsar nada
- **AND** ya no remite al administrador

#### Scenario: Sin red no se ofrece el contacto
- **WHEN** la pantalla de arranque muestra "Sin conexión a internet"
- **THEN** no muestra la tarjeta de soporte, porque sin red no se pueden abrir sus enlaces
