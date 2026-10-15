## MODIFIED Requirements

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
