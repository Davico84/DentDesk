## ADDED Requirements

### Requirement: Envío de correo seguro por defecto
Con el correo habilitado, el backend SHALL enviar por SMTP con STARTTLS obligatorio y autenticación, salvo que el modo local esté encendido explícitamente. Si el servidor no ofrece STARTTLS, el envío MUST fallar antes de transmitir el mensaje, en vez de seguir sin cifrar.

#### Scenario: Configuración por defecto
- **WHEN** el backend arranca con el correo habilitado y sin el modo local
- **THEN** el conector SMTP tiene STARTTLS habilitado y obligatorio, y autenticación habilitada

#### Scenario: Servidor sin STARTTLS en modo normal
- **WHEN** el modo local está apagado y el servidor SMTP no anuncia STARTTLS
- **THEN** el envío de la invitación falla con el mismo error de entrega que cualquier fallo SMTP
- **AND** la conversación SMTP termina antes de `DATA`: el mensaje no se transmite

### Requirement: Modo de correo local acotado a la máquina
Un modo local, apagado por defecto, SHALL permitir enviar a un servidor SMTP de prueba sin STARTTLS ni autenticación. Con el correo habilitado y el modo local encendido, el backend MUST negarse a arrancar si el host SMTP no es exactamente `localhost`, `127.0.0.1` o `::1`, sin mostrar credenciales en el error.

#### Scenario: Invitación en modo local
- **WHEN** el modo local está encendido, el host SMTP es `localhost` y se emite una invitación
- **THEN** el backend arranca
- **AND** la invitación se entrega al servidor de prueba sin `STARTTLS` ni `AUTH`

#### Scenario: Recuperación de contraseña en modo local
- **WHEN** el modo local está encendido con host `localhost` y se pide la recuperación de una cuenta activa
- **THEN** el enlace de recuperación se entrega al servidor de prueba sin `STARTTLS` ni `AUTH`

#### Scenario: Hosts locales aceptados
- **WHEN** el modo local está encendido y el host SMTP es `localhost`, `LOCALHOST`, `127.0.0.1`, `::1` o alguno de ellos con espacios alrededor
- **THEN** el backend arranca

#### Scenario: Host remoto o ambiguo rechazado
- **WHEN** el modo local está encendido y el host SMTP es `smtp-relay.brevo.com`, `localhost.`, `127.0.0.2`, `[::1]`, `0.0.0.0` o está vacío
- **THEN** el backend no arranca

#### Scenario: Error del arranque rechazado sin secretos
- **WHEN** el backend no arranca por un host no local en modo local
- **THEN** el error nombra `MAIL_LOCAL_SMTP` y, si es un nombre o una dirección, el host rechazado
- **AND** no contiene el usuario ni la contraseña SMTP

#### Scenario: Host con credenciales o caracteres de control
- **WHEN** el modo local está encendido y el host trae usuario y clave (`usuario:clave@host`), saltos de línea u otros caracteres de control
- **THEN** el backend no arranca
- **AND** el error nombra `MAIL_LOCAL_SMTP` pero no muestra ese valor

#### Scenario: Modo local con el correo deshabilitado
- **WHEN** el modo local está encendido pero el correo está deshabilitado, con cualquier host
- **THEN** el backend arranca igual que sin correo: la invitación se rechaza y los correos de la cuenta se descartan
