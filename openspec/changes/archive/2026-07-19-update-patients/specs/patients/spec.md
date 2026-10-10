## ADDED Requirements

### Requirement: Autoría de la ficha del paciente
El sistema SHALL registrar, para cada ficha de paciente, qué usuario la creó y qué usuario realizó
su última modificación, usando el mecanismo automático de autoría de `multi-tenancy`. Los pacientes
creados antes de que este mecanismo existiera SHALL conservar autoría desconocida en vez de un valor
inventado.

#### Scenario: Se registra la autoría al crear y editar
- **WHEN** un miembro de la clínica registra un paciente nuevo o edita uno existente
- **THEN** el sistema registra su identidad como autor de esa operación
- **AND** una edición posterior por otro miembro actualiza solo el autor de la última modificación

#### Scenario: Los pacientes anteriores a este mecanismo no tienen autoría inventada
- **WHEN** se consulta un paciente que existía antes de que el sistema registrara autoría
- **THEN** su autoría de creación y de última modificación **quedan sin asignar**
- **AND** el sistema no le atribuye un autor que no le corresponde
