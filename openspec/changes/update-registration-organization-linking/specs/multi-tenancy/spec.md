## MODIFIED Requirements

### Requirement: Alta de organización con su primer administrador

El alta de una organización SHALL dejar de ser autoservicio. La organización y su configuración SHALL nacer mediante el aprovisionamiento protegido definido en `add-platform-admin`; `POST /auth/register` solo SHALL aceptar la invitación nominativa del primer administrador y SHALL NOT crear la organización. Cada organización SHALL tener un `slug` único, derivado de su nombre e inmutable.

#### Scenario: Registro público no permite alta de organización
- **WHEN** un visitante intenta crear una organización desde `/auth/register`
- **THEN** el sistema rechaza la solicitud si no presenta una invitación válida
- **AND** no crea organización, configuración, identidad ni membresía

#### Scenario: Alta correcta de una organización
- **WHEN** una organización es aprovisionada por el flujo protegido de plataforma
- **THEN** el sistema crea la organización, su configuración y una invitación para su primer `ADMIN`

#### Scenario: El alta es atómica
- **WHEN** falla un paso del aprovisionamiento protegido
- **THEN** el sistema revierte la organización, configuración e invitación creadas

#### Scenario: Nombre de organización ausente o inválido
- **WHEN** el aprovisionamiento recibe un nombre ausente o inválido
- **THEN** el sistema rechaza la operación
- **AND** no crea una organización ni una invitación

#### Scenario: Alta atómica mediante aprovisionamiento
- **WHEN** falla un paso del aprovisionamiento protegido después de iniciar la creación de una organización
- **THEN** el sistema revierte la operación completa
- **AND** no queda una organización sin invitación para su primer administrador

#### Scenario: Dos organizaciones con el mismo nombre
- **WHEN** el aprovisionamiento protegido crea una organización cuyo nombre genera un `slug` que ya existe
- **THEN** el sistema crea la organización con un `slug` desambiguado y único
- **AND** el alta se completa con normalidad

#### Scenario: El slug no cambia al renombrar la organización
- **WHEN** una organización existente cambia su nombre visible
- **THEN** el sistema conserva el `slug` original
- **AND** las referencias previas al `slug` siguen resolviendo a la misma organización

