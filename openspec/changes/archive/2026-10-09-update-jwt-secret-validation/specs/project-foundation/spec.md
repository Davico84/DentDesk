## ADDED Requirements

### Requirement: Aviso de backend listo al arrancar
Cuando el backend termina de arrancar y ya acepta peticiones, SHALL escribir en el log un aviso destacado y fácil de localizar con el nombre del producto, la URL base con el puerto real y el estado de la documentación de la API. El aviso SHALL NOT incluir secretos ni otros valores sensibles de configuración.

#### Scenario: Aviso con la URL real
- **WHEN** la aplicación termina de arrancar
- **THEN** el log muestra un bloque delimitado con el nombre del producto (Dentdesk, o `app.name` si se define) y la URL `http://localhost:<puerto>` con el puerto en el que realmente escucha, incluido el context-path si hay uno

#### Scenario: Documentación de la API activa
- **WHEN** la aplicación arranca con Swagger UI habilitado (lo está por defecto)
- **THEN** el aviso incluye la URL de Swagger UI (`<url base>/swagger-ui.html`)

#### Scenario: Documentación de la API desactivada
- **WHEN** la aplicación arranca con `springdoc.swagger-ui.enabled=false`
- **THEN** el aviso indica que Swagger UI está desactivado

#### Scenario: Sin secretos en el aviso
- **WHEN** se escribe el aviso
- **THEN** no contiene el secreto JWT, la contraseña de la base de datos ni ningún otro valor de configuración sensible

#### Scenario: Sin servidor web no hay aviso
- **WHEN** el contexto arranca sin servidor web (por ejemplo, un test sin puerto)
- **THEN** no se escribe el aviso
