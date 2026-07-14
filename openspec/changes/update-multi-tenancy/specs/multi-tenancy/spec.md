## ADDED Requirements

### Requirement: El aislamiento no depende de que el desarrollador lo recuerde
El sistema SHALL establecer el contexto de organización **automáticamente** en toda transacción de una petición autenticada, sin que el código de negocio tenga que solicitarlo. Una capacidad de negocio nueva SHALL quedar aislada **sin escribir código de aislamiento**.

Un mecanismo que exija recordar una llamada acabará olvidándose, y **ese olvido no falla de forma visible: filtra datos de una clínica a otra**. Por eso la garantía tiene que ser **por defecto**, no por disciplina.

#### Scenario: Una entidad de negocio nueva queda aislada sin código propio
- **WHEN** se añade al sistema una entidad de negocio que **no incluye ninguna llamada** para establecer el contexto de organización
- **THEN** sus consultas quedan igualmente restringidas a la organización de la petición
- **AND** una organización distinta no puede leer ni modificar sus datos

#### Scenario: Una operación sin contexto de organización falla de forma ruidosa
- **WHEN** se intenta acceder a datos de negocio **sin** contexto de organización establecido (p. ej. fuera de una petición autenticada)
- **THEN** la operación **falla**
- **AND** NO devuelve un resultado vacío que pudiera confundirse con "no hay datos"

#### Scenario: Los flujos previos a la sesión siguen funcionando
- **WHEN** se ejecuta una operación en la que todavía no existe organización activa (el alta de una organización con su primer administrador)
- **THEN** la operación se completa correctamente
- **AND** el mecanismo automático no la bloquea por falta de contexto
