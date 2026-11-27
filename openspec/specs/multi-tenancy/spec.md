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
El sistema SHALL establecer el contexto de organización **automáticamente** en toda transacción de una petición autenticada, sin que el código de negocio lo solicite: una capacidad de negocio nueva SHALL quedar aislada **sin escribir código de aislamiento**. El código de negocio NO SHALL poder fijar el contexto de tenant por su cuenta; la única vía SHALL ser el mecanismo automático, **defendido por la build**. Un olvido aquí no falla de forma visible: filtra datos de una clínica a otra.

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
El sistema SHALL representar la identidad de una persona (`User`) una sola vez en la plataforma, con un correo único global y sin rol ni organización propios. La pertenencia de un usuario a una organización y su rol en ella SHALL modelarse como una membresía independiente. Un usuario SHALL poder tener membresías en varias organizaciones; el rol SHALL interpretarse siempre dentro de la organización de la membresía. La identidad y la membresía SHALL tener estados de ciclo de vida independientes.

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

El sistema SHALL determinar la organización activa exclusivamente a partir del claim `oid` del token para las peticiones de negocio. Las peticiones autenticadas a `/platform/**` con autoridad `ROLE_PLATFORM_ADMIN` SHALL poder operar sin claim `oid`; el filtro de tenant SHALL omitir esas rutas y no SHALL establecer un contexto de organización. El contexto SHALL quedar liberado al terminar cada petición.

#### Scenario: PLATFORM_ADMIN opera sin tenant
- **WHEN** un `PLATFORM_ADMIN` autenticado con autoridad `ROLE_PLATFORM_ADMIN` llama a `/platform/**` con un JWT sin `oid`
- **THEN** el sistema permite la operación global autorizada
- **AND** no establece contexto de organización

#### Scenario: Usuario de clínica no accede a plataforma
- **WHEN** un usuario con una membresía `ADMIN` o `MEMBER` llama a `/platform/**`
- **THEN** el sistema responde con acceso denegado
- **AND** no ejecuta ninguna operación global

#### Scenario: El contexto se deriva del token
- **WHEN** un usuario realiza una petición autenticada con un token de acceso válido cuya organización activa es A
- **THEN** el sistema establece la organización A como contexto de la transacción
- **AND** todas las consultas de negocio de esa petición quedan restringidas a la organización A

#### Scenario: El cliente no puede suplantar la organización
- **WHEN** un usuario cuya organización activa es A realiza una petición añadiendo una cabecera, un parámetro de ruta o un campo del cuerpo que indica la organización B
- **THEN** el sistema ignora ese valor
- **AND** la petición se resuelve en el contexto de la organización A

#### Scenario: Token de acceso sin organización activa
- **WHEN** se recibe una petición con un token de acceso válido en firma que no contiene el identificador de organización y no tiene autoridad `ROLE_PLATFORM_ADMIN`
- **THEN** el sistema rechaza la petición como no autenticada
- **AND** no establece ningún contexto de organización

#### Scenario: El contexto no sobrevive a la petición
- **WHEN** una petición cuya organización activa es A termina y el hilo que la atendió atiende después una petición cuya organización activa es B
- **THEN** la segunda petición se resuelve en el contexto de la organización B
- **AND** en ningún momento observa datos de la organización A

### Requirement: Alta de organización con su primer administrador

El sistema SHALL permitir que solo un `PLATFORM_ADMIN` autenticado cree una organización y su configuración, y emita en la misma operación una invitación nominativa con rol `ADMIN` para su primer administrador. La operación SHALL ser atómica y no SHALL crear directamente la cuenta del administrador.

#### Scenario: Plataforma crea una organización
- **WHEN** un `PLATFORM_ADMIN` envía datos válidos de una clínica
- **THEN** el sistema crea la organización y su configuración
- **AND** crea una invitación nominativa con rol `ADMIN`
- **AND** no crea directamente la cuenta del administrador

#### Scenario: Usuario no autorizado intenta aprovisionar
- **WHEN** un visitante o un `ADMIN` de clínica llama al endpoint de aprovisionamiento
- **THEN** el sistema rechaza la operación
- **AND** no crea organización, configuración ni invitación

#### Scenario: Registro público no permite alta de organización
- **WHEN** un visitante intenta crear una organización desde `/auth/register`
- **THEN** el sistema rechaza la solicitud si no presenta una invitación válida
- **AND** no crea organización, configuración, identidad ni membresía

#### Scenario: Alta atómica mediante aprovisionamiento
- **WHEN** falla la creación de la organización, configuración o invitación
- **THEN** el sistema revierte toda la operación
- **AND** no deja una organización sin su invitación inicial

#### Scenario: Alta correcta de una organización
- **WHEN** un visitante se registra aportando datos válidos mediante una invitación nominativa
- **THEN** el sistema crea la identidad y la membresía de la organización de la invitación
- **AND** establece la sesión con un token de acceso cuya organización activa es la invitación

#### Scenario: El alta es atómica
- **WHEN** un paso posterior falla después de haberse consumido una invitación válida
- **THEN** el sistema revierte la operación completa
- **AND** no queda una identidad sin membresía ni una membresía huérfana

#### Scenario: Dos organizaciones con el mismo nombre
- **WHEN** la plataforma aprovisiona una organización cuyo nombre genera un `slug` que ya existe
- **THEN** el sistema crea la organización con un `slug` desambiguado y único
- **AND** el aprovisionamiento se completa con normalidad

#### Scenario: El slug no cambia al renombrar la organización
- **WHEN** una organización existente cambia su nombre visible
- **THEN** el sistema conserva el `slug` original
- **AND** las referencias previas al `slug` siguen resolviendo a la misma organización

#### Scenario: Nombre de organización ausente o inválido
- **WHEN** la plataforma intenta aprovisionar una organización sin nombre o con un nombre que no cumple las validaciones mínimas
- **THEN** el sistema responde con un error de validación
- **AND** no crea la organización, su configuración ni la invitación inicial

### Requirement: Registro automático de autoría en entidades de negocio
El sistema SHALL registrar, para toda entidad de negocio que lo declare, qué usuario la creó y qué usuario realizó su última modificación. El sistema SHALL establecer esa autoría **automáticamente** a partir del contexto de identidad de la transacción, sin que el código de negocio tenga que escribirla. Sin usuario en el contexto (una operación previa a la sesión, como el alta de la primera organización), el sistema SHALL completar la operación con normalidad y dejar la autoría sin asignar.

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

### Requirement: Toda tabla del esquema está clasificada por su aislamiento
Toda tabla del esquema `public` SHALL figurar en exactamente una lista declarada de clasificación (por organización, por identidad, excepción por clínica, o sin organización), y la build MUST fallar ante una tabla sin clasificar o una entrada que ya no corresponda a ninguna tabla.

#### Scenario: Tabla nueva sin clasificar
- **WHEN** una migración crea una tabla que no figura en ninguna lista
- **THEN** la prueba de inventario falla y nombra la tabla

#### Scenario: Entrada obsoleta
- **WHEN** una lista nombra una tabla que ya no existe en el esquema
- **THEN** la prueba de inventario falla y nombra la entrada

#### Scenario: Tabla en dos listas
- **WHEN** una misma tabla figura en dos listas
- **THEN** la prueba de inventario falla y nombra la tabla

### Requirement: Las tablas aisladas por el motor tienen RLS forzada con política
Toda tabla clasificada como aislada por organización o por identidad SHALL tener Row-Level Security habilitada, forzada y al menos una política en el catálogo; las aisladas por organización MUST además tener la columna `organization_id`. Es una garantía estructural: que la política aísle bien lo prueban los tests de aislamiento de cada capacidad.

#### Scenario: Tabla aislada sin RLS forzada
- **WHEN** una tabla aislada por organización tiene RLS habilitada pero no forzada
- **THEN** la prueba de inventario falla y nombra la tabla

#### Scenario: Tabla aislada sin política
- **WHEN** una tabla aislada por identidad tiene RLS forzada y ninguna política
- **THEN** la prueba de inventario falla y nombra la tabla

#### Scenario: Cobertura completa
- **WHEN** todas las tablas aisladas cumplen RLS habilitada, forzada y con política
- **THEN** la prueba de inventario pasa

### Requirement: Toda excepción por clínica se declara con motivo y decisión
Una tabla con columna `organization_id` y sin RLS SHALL figurar como excepción por clínica con su motivo y el ADR que la decide, y NO SHALL figurar en la lista de tablas sin organización. Si una excepción gana RLS, la lista MUST actualizarse en el mismo cambio.

#### Scenario: Tabla con organization_id sin RLS y sin excepción
- **WHEN** una tabla con `organization_id` carece de RLS y no figura como excepción
- **THEN** la prueba de inventario falla y nombra la tabla

#### Scenario: Excepción que ganó RLS
- **WHEN** una tabla declarada como excepción pasa a tener RLS forzada con política
- **THEN** la prueba de inventario falla pidiendo moverla a la lista de aisladas

#### Scenario: Tabla sin organización que la adquiere
- **WHEN** una tabla de la lista sin organización gana una columna `organization_id`
- **THEN** la prueba de inventario falla y nombra la tabla

### Requirement: Toda función que salta la RLS se declara
Toda función o procedimiento `SECURITY DEFINER` de `public` que no pertenezca a una extensión SHALL figurar, por su firma, en una lista declarada con su motivo y los tests que lo cubren, y MUST fijar `search_path` a un valor permitido. La build MUST fallar ante una función sin declarar, una entrada obsoleta o un `search_path` ausente o no permitido.

#### Scenario: Función SECURITY DEFINER sin declarar
- **WHEN** una migración crea una función `SECURITY DEFINER` que no figura en la lista
- **THEN** la prueba de inventario falla y nombra la función

#### Scenario: Función sin search_path fijo
- **WHEN** una función declarada es `SECURITY DEFINER` y no fija `search_path`, o lo fija a un valor no permitido
- **THEN** la prueba de inventario falla y nombra la función

#### Scenario: Sobrecarga sin declarar
- **WHEN** una migración crea una sobrecarga `SECURITY DEFINER` de una función declarada, con otros argumentos
- **THEN** la prueba de inventario falla y nombra la firma nueva

#### Scenario: Funciones de extensiones ignoradas
- **WHEN** una extensión instala funciones en `public`
- **THEN** la prueba de inventario no las exige en la lista

### Requirement: Nada queda fuera del inventario sin decidirse
La prueba de inventario MUST fallar si en `public` existe una vista o vista materializada, si existe una tabla, vista o función en un esquema de usuario distinto de `public`, o si el rol con el que lee tiene `SUPERUSER` o `BYPASSRLS`, es dueño de una tabla de `public`, o puede asumir con `SET ROLE` un rol con esos atributos o al dueño. Cada uno de esos casos SHALL decidirse en su propio cambio.

#### Scenario: Vista nueva
- **WHEN** una migración crea una vista en `public`
- **THEN** la prueba de inventario falla y nombra la vista

#### Scenario: Objeto en otro esquema
- **WHEN** una migración crea una tabla en un esquema distinto de `public`
- **THEN** la prueba de inventario falla y nombra el esquema y la tabla

#### Scenario: Rol que salta la RLS
- **WHEN** el rol con el que lee la prueba tiene `BYPASSRLS`, o es miembro con opción `SET` de un rol que lo tiene
- **THEN** la prueba de inventario falla en vez de clasificar

#### Scenario: Rol que puede asumir al dueño de las tablas
- **WHEN** el rol con el que lee es miembro con opción `SET` del rol dueño de las tablas de `public`
- **THEN** la prueba de inventario falla

#### Scenario: Membresía solo con INHERIT
- **WHEN** el rol con el que lee es miembro sin opción `SET` de un rol con `BYPASSRLS`
- **THEN** esa membresía no hace fallar la prueba

### Requirement: El inventario se verifica como rol de aplicación y se prueba fallando
La prueba de inventario SHALL leer el catálogo de PostgreSQL conectada como el rol de aplicación no superusuario, sin depender de vistas filtradas por privilegios, y su lógica de clasificación MUST tener pruebas con instantáneas sintéticas que demuestren que cada violación hace fallar la regla.

#### Scenario: Catálogo vacío no pasa como verde
- **WHEN** la lectura del catálogo no devuelve las tablas esperadas (p. ej. `patients` e `invitations`)
- **THEN** la prueba de inventario falla en vez de pasar vacía

#### Scenario: Cada violación se detecta
- **WHEN** la lógica de clasificación recibe una instantánea con una violación deliberada de cada regla
- **THEN** devuelve una violación que nombra la tabla y la regla incumplida
