## ADDED Requirements

### Requirement: Envío de correo seguro por defecto
Con el correo habilitado, el backend SHALL enviar por SMTP exigiendo STARTTLS y autenticación, salvo que el modo local esté encendido explícitamente. Si el servidor no ofrece STARTTLS, el envío MUST fallar en vez de seguir sin cifrar.

#### Scenario: Configuración por defecto
- **WHEN** el backend arranca con el correo habilitado y sin el modo local
- **THEN** el conector SMTP exige STARTTLS y autenticación

#### Scenario: Servidor sin STARTTLS en modo normal
- **WHEN** el modo local está apagado y el servidor SMTP no ofrece STARTTLS
- **THEN** el envío falla con el mismo error de entrega que cualquier fallo SMTP
- **AND** el correo no sale sin cifrar

### Requirement: Modo de correo local acotado a la máquina
Un modo local, apagado por defecto, SHALL permitir enviar a un servidor SMTP de prueba sin STARTTLS ni autenticación. Encendido, el backend MUST negarse a arrancar si el host SMTP no es local (`localhost`, `127.0.0.1` o `::1`), sin mostrar credenciales en el error.

#### Scenario: Modo local contra un servidor de prueba local
- **WHEN** el modo local está encendido y el host SMTP es `localhost`
- **THEN** el backend arranca
- **AND** las invitaciones y los correos de la cuenta se entregan al servidor de prueba sin STARTTLS ni autenticación

#### Scenario: Modo local con un host remoto
- **WHEN** el modo local está encendido y el host SMTP es un servidor remoto
- **THEN** el backend no arranca
- **AND** el error nombra la variable del modo local y el host rechazado, sin usuario ni contraseña

#### Scenario: Modo local con el correo deshabilitado
- **WHEN** el modo local está encendido pero el correo está deshabilitado
- **THEN** el backend arranca igual que sin correo: la invitación se rechaza y los correos de la cuenta se descartan
