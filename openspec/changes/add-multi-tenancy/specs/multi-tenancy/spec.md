## ADDED Requirements

### Requirement: Aislamiento de datos de negocio entre clínicas
El sistema SHALL garantizar que ninguna operación ejecutada en el contexto de una clínica pueda leer ni modificar datos de negocio de otra clínica. El aislamiento SHALL aplicarse sobre las tablas de negocio en dos capas independientes: filtrado automático en la capa de persistencia y políticas de seguridad a nivel de fila en el motor de base de datos. La barrera del motor SHALL seguir protegiendo aunque la capa de aplicación omita el filtro. Las tablas del dominio de autenticación (identidad, membresía, tokens de refresco) NO son datos de negocio y no participan de este aislamiento.

#### Scenario: Una consulta de negocio solo alcanza datos de su propia clínica
- **WHEN** un usuario autenticado en la clínica A consulta un recurso de negocio
- **THEN** el sistema devuelve únicamente filas cuya clínica es A
- **AND** no incluye ninguna fila de la clínica B

#### Scenario: Una consulta que omite el filtro de clínica sigue estando aislada
- **WHEN** se ejecuta, en el contexto de la clínica A, una consulta sobre una tabla de negocio que **no** incluye condición sobre la clínica
- **THEN** el motor de base de datos devuelve únicamente las filas de la clínica A
- **AND** las filas de la clínica B permanecen inaccesibles

#### Scenario: Acceso directo por identificador a un recurso de otra clínica
- **WHEN** un usuario autenticado en la clínica A solicita por su identificador un recurso de negocio que pertenece a la clínica B
- **THEN** el sistema responde como si el recurso no existiera
- **AND** no revela la existencia del recurso ni datos de la clínica B

#### Scenario: Operación de negocio sin clínica en el contexto
- **WHEN** se intenta ejecutar una operación sobre una tabla de negocio sin que exista una clínica en el contexto de la transacción
- **THEN** la operación falla
- **AND** no devuelve datos de ninguna clínica

### Requirement: Identidad global separada de la pertenencia a la clínica
El sistema SHALL representar la identidad de una persona (`User`) una sola vez en toda la plataforma, con un correo único global, sin rol ni clínica propios. La pertenencia de un usuario a una clínica y su rol en ella SHALL modelarse como una membresía independiente. Un usuario SHALL poder tener membresías en varias clínicas; el rol SHALL interpretarse siempre dentro de la clínica de la membresía.

#### Scenario: Un correo identifica a una única persona
- **WHEN** un visitante intenta registrarse con un correo que ya pertenece a una identidad existente
- **THEN** el sistema responde con un error de conflicto
- **AND** no crea una identidad duplicada

#### Scenario: El rol pertenece a la membresía, no a la identidad
- **WHEN** se consulta el rol de un usuario en una clínica concreta
- **THEN** el sistema devuelve el rol registrado en su membresía con esa clínica
- **AND** ese rol no condiciona su rol en ninguna otra clínica

#### Scenario: El rol de administrador está acotado a su clínica
- **WHEN** un usuario con rol `ADMIN` en la clínica A opera sobre recursos administrativos
- **THEN** el sistema le permite administrar los recursos de la clínica A
- **AND** le deniega el acceso a los recursos de la clínica B con el mismo tratamiento que a un recurso inexistente

### Requirement: Resolución del contexto de clínica en cada petición autenticada
El sistema SHALL determinar la clínica activa de una petición autenticada **exclusivamente** a partir del token de acceso firmado, nunca a partir de datos suministrados por el cliente (cabeceras, ruta o cuerpo). El token de acceso SHALL incluir el identificador de la clínica activa. El contexto de clínica SHALL quedar liberado al terminar la petición, de modo que no pueda observarse desde una petición posterior atendida por el mismo hilo.

#### Scenario: El contexto se deriva del token
- **WHEN** un usuario realiza una petición autenticada con un token de acceso válido cuya clínica activa es A
- **THEN** el sistema establece la clínica A como contexto de la transacción
- **AND** todas las consultas de negocio de esa petición quedan restringidas a la clínica A

#### Scenario: El cliente no puede suplantar la clínica
- **WHEN** un usuario cuya clínica activa es A realiza una petición añadiendo una cabecera, un parámetro de ruta o un campo del cuerpo que indica la clínica B
- **THEN** el sistema ignora ese valor
- **AND** la petición se resuelve en el contexto de la clínica A

#### Scenario: Token de acceso sin clínica activa
- **WHEN** se recibe una petición con un token de acceso válido en firma pero que no contiene el identificador de clínica
- **THEN** el sistema rechaza la petición como no autenticada
- **AND** no establece ningún contexto de clínica

#### Scenario: El contexto no sobrevive a la petición
- **WHEN** una petición cuya clínica activa es A termina y el hilo que la atendió atiende después una petición cuya clínica activa es B
- **THEN** la segunda petición se resuelve en el contexto de la clínica B
- **AND** en ningún momento observa datos de la clínica A

### Requirement: Alta de clínica con su primer administrador
El sistema SHALL crear una clínica, la identidad de su primer usuario y la membresía que los une como una única operación atómica, iniciada por el registro autoservicio. La membresía creada SHALL tener rol de administrador. Si cualquier paso falla, el sistema SHALL no dejar ni clínicas sin administrador, ni identidades sin membresía, ni membresías huérfanas. Cada clínica SHALL tener un identificador legible (`slug`) único, derivado de su nombre.

#### Scenario: Alta correcta de una clínica
- **WHEN** un visitante se registra aportando el nombre de la clínica junto con su nombre completo, correo y contraseña válidos
- **THEN** el sistema crea la clínica en estado activo
- **AND** crea la identidad del usuario
- **AND** crea una membresía con rol `ADMIN` que une al usuario con la clínica
- **AND** establece la sesión con un token de acceso cuya clínica activa es la recién creada

#### Scenario: El alta es atómica
- **WHEN** un paso posterior falla después de haberse creado la clínica
- **THEN** el sistema revierte la operación completa
- **AND** no queda ninguna clínica sin administrador ni identidad sin membresía

#### Scenario: Dos clínicas con el mismo nombre
- **WHEN** un visitante registra una clínica cuyo nombre genera un `slug` que ya existe
- **THEN** el sistema crea la clínica con un `slug` desambiguado y único
- **AND** el alta se completa con normalidad

#### Scenario: Nombre de clínica ausente o inválido
- **WHEN** un visitante intenta registrarse sin nombre de clínica o con un nombre que no cumple las validaciones mínimas
- **THEN** el sistema responde con un error de validación
- **AND** no crea ni la clínica, ni la identidad, ni la membresía
