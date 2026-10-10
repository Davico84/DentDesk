## ADDED Requirements

### Requirement: Registro automático de autoría en entidades de negocio
El sistema SHALL registrar, para toda entidad de negocio que lo declare, qué usuario la creó y qué
usuario realizó su última modificación. El sistema SHALL establecer esa autoría **automáticamente**
a partir del contexto de identidad de la transacción, sin que el código de negocio tenga que
escribirla. Cuando no exista un usuario en el contexto (una operación previa a la sesión, como el
alta de la primera organización), el sistema SHALL completar la operación con normalidad y dejar la
autoría sin asignar, en vez de fallar.

#### Scenario: Se registra quién creó y quién modificó por última vez
- **WHEN** un usuario autenticado crea o modifica una entidad de negocio que declara autoría
- **THEN** el sistema registra su identidad como autor de la operación
- **AND** una modificación posterior por otro usuario actualiza solo el autor de la última
  modificación, sin alterar el autor de la creación original

#### Scenario: Una entidad nueva registra autoría sin código propio
- **WHEN** se añade al sistema una entidad de negocio que declara autoría pero **no incluye ninguna
  llamada** para establecerla
- **THEN** el sistema la rellena igualmente a partir del contexto de la transacción
- **AND** el código de negocio no necesita leer ni propagar la identidad del usuario

#### Scenario: Los flujos previos a la sesión siguen funcionando
- **WHEN** se ejecuta una operación en la que todavía no existe un usuario en el contexto (el alta
  de una organización con su primer administrador)
- **THEN** la operación se completa correctamente
- **AND** la autoría queda sin asignar, en vez de bloquear la operación
