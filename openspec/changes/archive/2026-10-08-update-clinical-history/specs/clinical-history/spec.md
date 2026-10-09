## MODIFIED Requirements

### Requirement: Información inicial sellada tras COMPLETED

Una vez en `COMPLETED`, la información inicial de la Historia Clínica SHALL quedar sellada, legible en todo momento. La **API SHALL rechazar en el servidor** toda modificación, **independientemente del cliente**, aunque la petición llegue directo al endpoint. Una escritura concurrente con el sellado NO SHALL reabrir la Historia ni modificarla después de sellada. La corrección posterior de un dato queda fuera de esta capacidad (enmienda auditable, fase posterior).

#### Scenario: La Historia sellada permanece legible
- **WHEN** un usuario abre una Historia Clínica en estado `COMPLETED`
- **THEN** el sistema muestra toda su información en modo lectura

#### Scenario: Rechazar la edición de la información sellada aun por API directa
- **WHEN** se envía directamente a la API una petición para modificar el diagnóstico, los antecedentes u otra sección inicial de una Historia `COMPLETED` (sin pasar por la UI)
- **THEN** el servidor rechaza la modificación

#### Scenario: Guardar la Historia mientras otra operación la sella no la reabre
- **WHEN** un usuario guarda la información inicial de una Historia en `DRAFT` mientras otra operación la está sellando
- **THEN** el sistema rechaza el guardado porque la Historia quedó sellada
- **AND** la Historia permanece en `COMPLETED`, sin perder el sellado

#### Scenario: Los diagnósticos de una Historia que se está sellando no cambian
- **WHEN** un usuario agrega, edita o elimina un diagnóstico de una Historia en `DRAFT` mientras otra operación la está sellando
- **THEN** el sistema rechaza la operación porque la Historia quedó sellada
- **AND** la Historia sellada conserva sus diagnósticos tal como estaban al sellarse
