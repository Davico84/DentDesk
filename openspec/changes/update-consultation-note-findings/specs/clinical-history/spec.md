## ADDED Requirements

### Requirement: Hallazgos del odontograma atendidos en la nota

Mientras la nota esté `ABIERTA`, el sistema SHALL permitir marcar y desmarcar hallazgos del odontograma del mismo paciente como atendidos en esa consulta, ofreciendo primero los que se ven rojos. Cada hallazgo atendido SHALL guardar una descripción tomada del hallazgo al marcarlo (pieza, cara, hallazgo y estado), su fecha y su autor, y SHALL quedar inmutable al sellarse la nota.

#### Scenario: Marcar un hallazgo pendiente como atendido
- **WHEN** un usuario marca como atendida, en una nota `ABIERTA`, una lesión de caries en la cara oclusal de la pieza 16
- **THEN** el sistema lo registra en la nota con su descripción, la fecha y el usuario
- **AND** la nota lo muestra entre sus hallazgos atendidos

#### Scenario: Los hallazgos que se ven rojos aparecen primero
- **WHEN** un usuario abre los hallazgos del odontograma desde una nota `ABIERTA`
- **THEN** el sistema muestra primero, como pendientes, los que se ven rojos (siempre rojos, o según su estado y en mal estado)
- **AND** ofrece los demás por separado, plegados

#### Scenario: Un pendiente ya atendido en una consulta anterior
- **WHEN** un hallazgo rojo fue marcado como atendido en una nota ya sellada
- **THEN** el panel indica la fecha de esa consulta
- **AND** el hallazgo se puede volver a marcar en la nota abierta

#### Scenario: Desmarcar un hallazgo mientras la nota está abierta
- **WHEN** un usuario desmarca un hallazgo atendido de una nota `ABIERTA`
- **THEN** el sistema quita el registro y la nota deja de mostrarlo

#### Scenario: Rechazar marcar dos veces el mismo hallazgo en una nota
- **WHEN** un usuario intenta marcar como atendido un hallazgo que ya está marcado en la misma nota
- **THEN** el sistema rechaza la operación y conserva el registro existente

#### Scenario: Rechazar un hallazgo de otro paciente
- **WHEN** un usuario intenta marcar en la nota un hallazgo del odontograma de otro paciente, o de otra clínica
- **THEN** el sistema rechaza la operación; el hallazgo de otra clínica se trata como inexistente

#### Scenario: Los hallazgos atendidos quedan fijos al sellar
- **WHEN** se envía directo a la API una petición para marcar o desmarcar un hallazgo en una nota `SELLADA`
- **THEN** el servidor rechaza la operación y la nota no cambia

#### Scenario: Marcar o desmarcar en paralelo con el sellado
- **WHEN** un usuario marca o desmarca un hallazgo de una nota `ABIERTA` mientras otra operación la está sellando
- **THEN** el sistema rechaza la operación porque la nota quedó sellada
- **AND** la nota sellada conserva los hallazgos atendidos que tenía al sellarse

#### Scenario: La nota sellada se lee sola
- **WHEN** un usuario lee una nota `SELLADA` con hallazgos atendidos
- **THEN** el sistema muestra cada hallazgo con la descripción guardada al marcarlo

### Requirement: Diagnóstico propuesto desde un hallazgo atendido

Desde un hallazgo atendido, el sistema SHALL proponer un diagnóstico con la ubicación del hallazgo (la pieza si es de una cara o de una pieza; general en los demás casos) y, para los hallazgos siempre rojos con sugerencia, los códigos CIE10 sugeridos. Siempre SHALL poder buscarse cualquier código del catálogo. El diagnóstico SHALL registrarse solo al confirmarlo el usuario y con las reglas de diagnóstico vigentes.

#### Scenario: Caries en dentina con su sugerencia
- **WHEN** un usuario propone el diagnóstico de una lesión de caries a nivel de la dentina en la pieza 16
- **THEN** el formulario trae la ubicación en la pieza 16 y sugiere el código K021 (caries de la dentina)
- **AND** el usuario elige el tipo P, D o R y lo confirma

#### Scenario: Hallazgo entre piezas, en un rango o en un arco
- **WHEN** un usuario propone el diagnóstico de un hallazgo registrado entre dos piezas, en un rango o en un arco
- **THEN** el formulario trae la ubicación general

#### Scenario: Hallazgo sin sugerencia
- **WHEN** un usuario propone el diagnóstico de un hallazgo que se ve rojo por estar en mal estado, como una corona
- **THEN** el formulario no sugiere códigos y ofrece el buscador del catálogo

#### Scenario: Elegir un código distinto del sugerido
- **WHEN** el formulario sugiere códigos y el usuario busca y elige otro código del catálogo
- **THEN** el sistema registra el diagnóstico con el código elegido por el usuario

#### Scenario: Nada se registra sin confirmar
- **WHEN** un usuario abre la propuesta de diagnóstico de un hallazgo y la cierra sin confirmar
- **THEN** la nota no gana ningún diagnóstico

#### Scenario: Las sugerencias existen en el catálogo
- **WHEN** el sistema sugiere códigos para cualquier tipo de hallazgo
- **THEN** cada código sugerido pertenece al catálogo CIE10 vigente

## MODIFIED Requirements

### Requirement: Nota abierta editable y descartable solo vacía

Mientras la nota esté `ABIERTA`, el sistema SHALL permitir editar sus campos, diagnósticos, antecedentes y hallazgos atendidos en varias sesiones. El sistema SHALL permitir **descartar** una nota abierta solo si no tiene contenido, diagnósticos, antecedentes ni hallazgos atendidos; con contenido, la nota NO SHALL poder descartarse.

#### Scenario: Retomar y editar una nota abierta
- **WHEN** un usuario reabre una nota `ABIERTA` y modifica un campo
- **THEN** el sistema guarda el cambio y la mantiene `ABIERTA`

#### Scenario: Descartar una nota abierta vacía
- **WHEN** un usuario descarta una nota `ABIERTA` sin contenido, sin diagnósticos, sin antecedentes y sin hallazgos atendidos
- **THEN** el sistema la elimina y el paciente puede abrir otra

#### Scenario: Rechazar el descarte de una nota con contenido
- **WHEN** un usuario intenta descartar una nota `ABIERTA` que tiene algún campo, diagnóstico, antecedente o hallazgo atendido registrado
- **THEN** el sistema rechaza la operación y conserva la nota
