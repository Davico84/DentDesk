## MODIFIED Requirements

### Requirement: Toda la API falla con un único formato de error
El sistema SHALL responder **todos** sus errores con el mismo formato (RFC 9457), sin excepciones. Ninguna respuesta de error SHALL usar el formato por defecto del contenedor. Sin esta uniformidad, el contrato no puede describir los errores con un único schema y el cliente no puede tipar el manejo de errores.

Además de la **forma**, el **contenido** SHALL ser apto para el usuario final: el campo `detail` de **cualquier** respuesta de error SHALL ser un mensaje redactado para mostrarse directamente en la interfaz, en el idioma del producto. Esto SHALL cumplirse también para los errores que resuelve el manejador de excepciones **por defecto** del framework (petición mal formada, método no permitido, tipo de contenido no soportado, recurso inexistente), que de otro modo emiten el mensaje interno del framework. El sistema NO SHALL exponer en `detail` texto técnico dirigido a desarrolladores. El cliente muestra `detail` tal cual, así que un mensaje interno filtrado llega íntegro al usuario.

#### Scenario: El error de sesión ausente usa el formato común
- **WHEN** se solicita un recurso protegido **sin autenticarse**
- **THEN** la respuesta es `401`
- **AND** su cuerpo tiene el **mismo formato** que el resto de errores de la API (RFC 9457), no el cuerpo por defecto del contenedor

#### Scenario: Un error de negocio usa el formato común
- **WHEN** se intenta iniciar sesión con credenciales inválidas
- **THEN** la respuesta es `401` con el formato común (RFC 9457)

#### Scenario: Un error de permisos usa el formato común
- **WHEN** un usuario autenticado sin el rol requerido invoca una operación restringida
- **THEN** la respuesta es `403` con el formato común (RFC 9457)

#### Scenario: Un error resuelto por el framework trae un mensaje apto para el usuario
- **WHEN** se envía una petición con el cuerpo mal formado, que resuelve el manejador de excepciones por defecto del framework
- **THEN** la respuesta conserva su código de estado y el formato común (RFC 9457)
- **AND** su `detail` es un mensaje en el idioma del producto, redactado para mostrarse al usuario
- **AND** su `detail` no es el mensaje interno que genera el framework

#### Scenario: Un error sin mensaje específico no filtra texto interno
- **WHEN** el framework resuelve un error cuyo código de estado no tiene un mensaje propio definido
- **THEN** el `detail` de la respuesta es un mensaje genérico apto para el usuario
- **AND** en ningún caso se expone el texto interno del framework
