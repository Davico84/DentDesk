# Spec Delta — project-foundation

## ADDED Requirements

### Requirement: Aviso de espera durante el uso
Con la app ya iniciada, si alguna petición a la API sigue sin responder a los 4 segundos, la app SHALL mostrar un aviso "Despertando el servidor…" que desaparece cuando no queda ninguna petición lenta en curso. Pasados 90 segundos SHALL ofrecer "Reintentar" y el contacto de soporte; sin conexión, SHALL decir "Sin conexión a internet". Mientras la app se inicia, la pantalla de arranque en frío SHALL ser el único aviso.

#### Scenario: Petición rápida sin aviso
- **WHEN** una petición a la API responde antes de 4 segundos
- **THEN** no aparece ningún aviso

#### Scenario: Petición lenta durante el uso
- **WHEN** con la app iniciada una petición sigue pendiente a los 4 segundos
- **THEN** aparece "Despertando el servidor…" en una región `status`
- **AND** al responder la petición, el aviso desaparece y la pantalla continúa con los datos

#### Scenario: Varias peticiones lentas a la vez
- **WHEN** dos peticiones siguen pendientes a los 4 segundos y una responde antes que la otra
- **THEN** el aviso sigue visible hasta que responde la última

#### Scenario: Petición lenta que termina con error
- **WHEN** una petición lenta termina con un error
- **THEN** el aviso desaparece y la pantalla maneja el error como siempre

#### Scenario: Espera demasiado larga
- **WHEN** una petición sigue pendiente a los 90 segundos
- **THEN** el aviso ofrece "Reintentar", que recarga la app, y los botones de soporte

#### Scenario: Sin conexión durante una espera
- **WHEN** hay una petición lenta y el navegador está sin conexión
- **THEN** el aviso dice "Sin conexión a internet" en lugar de "Despertando el servidor…"

#### Scenario: Durante el arranque no se duplica el aviso
- **WHEN** la app se está iniciando y la restauración de la sesión tarda más de 4 segundos
- **THEN** se ve la pantalla de arranque en frío y no el aviso de espera
