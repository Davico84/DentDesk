# multi-tenancy Specification

## Purpose

Aísla los datos de cada organización (tenant) en un SaaS multi-tenant. Define el modelo identidad/organización/membresía (`User` global sin rol · `Organization` como tenant con `slug` inmutable y `OrganizationSettings` 1:1 · `Membership` puente que porta el rol), la resolución del contexto de organización por petición (solo desde el token firmado) y el alta autoservicio de organización con su primer administrador. Es una capacidad **técnica/plataforma** reutilizable. El aislamiento se implementa en dos capas —filtrado de aplicación (`@TenantId`) y Row-Level Security en PostgreSQL, con dos claves: identidad para `memberships`, organización para el negocio— y las reglas de construcción viven en `docs/backend.md §8.1`.

## Requirements

### Requirement: Aislamiento de datos de negocio entre organizaciones
El sistema SHALL garantizar que ninguna operación ejecutada en el contexto de una organización pueda leer ni modificar datos de negocio de otra organización. El aislamiento SHALL aplicarse sobre las tablas de negocio en dos capas independientes: filtrado automático en la capa de persistencia y políticas de seguridad a nivel de fila en el motor de base de datos. La barrera del motor SHALL seguir protegiendo aunque la capa de aplicación omita el filtro.

#### Scenario: Una consulta de negocio solo alcanza datos de su propia organización
- **WHEN** un usuario autenticado en la organización A consulta un recurso de negocio
- **THEN** el sistema devuelve únicamente filas cuya organización es A
- **AND** no incluye ninguna fila de la organización B

#### Scenario: Una consulta que omite el filtro sigue estando aislada
- **WHEN** se ejecuta, en el contexto de la organización A, una consulta sobre una tabla de negocio que **no** incluye condición sobre la organización
- **THEN** el motor de base de datos devuelve únicamente las filas de la organización A
- **AND** las filas de la organización B permanecen inaccesibles

#### Scenario: Acceso directo por identificador a un recurso de otra organización
- **WHEN** un usuario autenticado en la organización A solicita por su identificador un recurso de negocio que pertenece a la organización B
- **THEN** el sistema responde como si el recurso no existiera
- **AND** no revela la existencia del recurso ni datos de la organización B

#### Scenario: Operación de negocio sin organización en el contexto
- **WHEN** se intenta ejecutar una operación sobre una tabla de negocio sin que exista una organización en el contexto de la transacción
- **THEN** la operación falla
- **AND** no devuelve datos de ninguna organización

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

### Requirement: Aislamiento de la pertenencia por identidad
El sistema SHALL garantizar, a nivel de motor de base de datos, que un usuario solo pueda leer las membresías asociadas a su propia identidad. Esta garantía NO SHALL depender de que la capa de aplicación recuerde filtrar por el usuario.

#### Scenario: Un usuario solo ve sus propias membresías
- **WHEN** un usuario autenticado consulta sus membresías
- **THEN** el sistema devuelve únicamente las membresías de ese usuario
- **AND** no revela membresías de ningún otro usuario

#### Scenario: Una consulta de membresías que omite el filtro sigue estando aislada
- **WHEN** se ejecuta, en el contexto de un usuario autenticado, una consulta sobre las membresías que **no** incluye condición sobre el usuario
- **THEN** el motor de base de datos devuelve únicamente las membresías de ese usuario
- **AND** las membresías de otros usuarios permanecen inaccesibles

### Requirement: Identidad global separada de la pertenencia a la organización
El sistema SHALL representar la identidad de una persona (`User`) una sola vez en toda la plataforma, con un correo único global, sin rol ni organización propios. La pertenencia de un usuario a una organización y su rol en ella SHALL modelarse como una membresía independiente. Un usuario SHALL poder tener membresías en varias organizaciones; el rol SHALL interpretarse siempre dentro de la organización de la membresía. La identidad y la membresía SHALL tener estados de ciclo de vida independientes.

#### Scenario: Un correo identifica a una única persona
- **WHEN** un visitante intenta registrarse con un correo que ya pertenece a una identidad existente
- **THEN** el sistema responde con un error de conflicto
- **AND** no crea una identidad duplicada

#### Scenario: El rol pertenece a la membresía, no a la identidad
- **WHEN** se consulta el rol de un usuario en una organización concreta
- **THEN** el sistema devuelve el rol registrado en su membresía con esa organización
- **AND** ese rol no condiciona su rol en ninguna otra organización

#### Scenario: El rol de administrador está acotado a su organización
- **WHEN** un usuario con rol `ADMIN` en la organización A opera sobre recursos administrativos
- **THEN** el sistema le permite administrar los recursos de la organización A
- **AND** le deniega el acceso a los recursos de la organización B con el mismo tratamiento que a un recurso inexistente

#### Scenario: Deshabilitar una membresía no deshabilita la identidad
- **WHEN** la membresía de un usuario en la organización A queda inactiva pero su identidad sigue activa y tiene otra membresía en la organización B
- **THEN** el usuario deja de acceder a la organización A
- **AND** conserva el acceso a la organización B

### Requirement: Resolución del contexto de organización en cada petición autenticada
El sistema SHALL determinar la organización activa de una petición autenticada **exclusivamente** a partir del token de acceso firmado, nunca a partir de datos suministrados por el cliente (cabeceras, ruta o cuerpo). El token de acceso SHALL incluir el identificador de la organización activa. El contexto SHALL quedar liberado al terminar la petición, de modo que no pueda observarse desde una petición posterior atendida por el mismo hilo.

#### Scenario: El contexto se deriva del token
- **WHEN** un usuario realiza una petición autenticada con un token de acceso válido cuya organización activa es A
- **THEN** el sistema establece la organización A como contexto de la transacción
- **AND** todas las consultas de negocio de esa petición quedan restringidas a la organización A

#### Scenario: El cliente no puede suplantar la organización
- **WHEN** un usuario cuya organización activa es A realiza una petición añadiendo una cabecera, un parámetro de ruta o un campo del cuerpo que indica la organización B
- **THEN** el sistema ignora ese valor
- **AND** la petición se resuelve en el contexto de la organización A

#### Scenario: Token de acceso sin organización activa
- **WHEN** se recibe una petición con un token de acceso válido en firma pero que no contiene el identificador de organización
- **THEN** el sistema rechaza la petición como no autenticada
- **AND** no establece ningún contexto de organización

#### Scenario: El contexto no sobrevive a la petición
- **WHEN** una petición cuya organización activa es A termina y el hilo que la atendió atiende después una petición cuya organización activa es B
- **THEN** la segunda petición se resuelve en el contexto de la organización B
- **AND** en ningún momento observa datos de la organización A

### Requirement: Alta de organización con su primer administrador
El sistema SHALL crear una organización, su configuración por defecto, la identidad de su primer usuario y la membresía que los une como una única operación atómica, iniciada por el registro autoservicio. La membresía creada SHALL tener rol de administrador. Si cualquier paso falla, el sistema SHALL no dejar ni organizaciones sin administrador, ni identidades sin membresía, ni membresías huérfanas. Cada organización SHALL tener un identificador legible (`slug`) único, derivado de su nombre e **inmutable** tras la creación.

#### Scenario: Alta correcta de una organización
- **WHEN** un visitante se registra aportando el nombre de la organización junto con su nombre completo, correo y contraseña válidos
- **THEN** el sistema crea la organización en estado activo con su configuración por defecto
- **AND** crea la identidad del usuario
- **AND** crea una membresía con rol `ADMIN` que une al usuario con la organización
- **AND** establece la sesión con un token de acceso cuya organización activa es la recién creada

#### Scenario: El alta es atómica
- **WHEN** un paso posterior falla después de haberse creado la organización
- **THEN** el sistema revierte la operación completa
- **AND** no queda ninguna organización sin administrador ni identidad sin membresía

#### Scenario: Dos organizaciones con el mismo nombre
- **WHEN** un visitante registra una organización cuyo nombre genera un `slug` que ya existe
- **THEN** el sistema crea la organización con un `slug` desambiguado y único
- **AND** el alta se completa con normalidad

#### Scenario: El slug no cambia al renombrar la organización
- **WHEN** una organización existente cambia su nombre visible
- **THEN** el sistema conserva el `slug` original
- **AND** las referencias previas al `slug` siguen resolviendo a la misma organización

#### Scenario: Nombre de organización ausente o inválido
- **WHEN** un visitante intenta registrarse sin nombre de organización o con un nombre que no cumple las validaciones mínimas
- **THEN** el sistema responde con un error de validación
- **AND** no crea ni la organización, ni la configuración, ni la identidad, ni la membresía

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
