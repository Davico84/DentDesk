## ADDED Requirements

### Requirement: El aislamiento no depende de que el desarrollador lo recuerde
El sistema SHALL establecer el contexto de organización **automáticamente** en toda transacción de una petición autenticada, sin que el código de negocio tenga que solicitarlo. Una capacidad de negocio nueva SHALL quedar aislada **sin escribir código de aislamiento**.

El código de negocio NO SHALL poder fijar el contexto de tenant por su cuenta: la única vía SHALL ser el mecanismo automático. Un mecanismo que exija recordar una llamada acabará olvidándose, y **ese olvido no falla de forma visible: filtra datos de una clínica a otra**. Por eso la garantía tiene que ser **por defecto**, no por disciplina — y estar **defendida por la build**, no por un documento.

#### Scenario: Una entidad de negocio nueva queda aislada sin código propio
- **WHEN** se añade al sistema una entidad de negocio que **no incluye ninguna llamada** para establecer el contexto de organización
- **THEN** sus consultas quedan igualmente restringidas a la organización de la petición
- **AND** una organización distinta no puede leer ni modificar sus datos

#### Scenario: El código de negocio no puede fijar el contexto a mano
- **WHEN** una clase de las capas de negocio o de presentación intenta establecer el contexto de tenant por su cuenta
- **THEN** **la build falla**
- **AND** el aislamiento no puede degradarse a "acordarse de llamar"

#### Scenario: Los flujos previos a la sesión siguen funcionando
- **WHEN** se ejecuta una operación en la que todavía no existe organización activa (el alta de una organización con su primer administrador)
- **THEN** la operación se completa correctamente
- **AND** el mecanismo automático no la bloquea por falta de contexto
