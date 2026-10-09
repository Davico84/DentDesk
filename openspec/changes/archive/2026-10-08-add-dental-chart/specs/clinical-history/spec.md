> ℹ️ **Este delta toca solo el requirement de *sellado*.** [`ADR-0015`](../../../../docs/adr/0015-identidad-del-paciente.md)
> exige la filiación completa del paciente en la **apertura** de la Historia Clínica — un requirement **distinto**
> de esta misma capacidad. Por eso ambos changes pueden implementarse por separado **sin componer deltas**:
> ninguno reescribe el requirement del otro.

## MODIFIED Requirements

### Requirement: Sellado a COMPLETED con checklist mínimo afirmativo
La transición de `DRAFT` a `COMPLETED` SHALL ser manual, explícita y **unidireccional** (sin volver a `DRAFT` ni resellar), y SHALL validar un **checklist mínimo**. En alergias, medicación y antecedentes familiares un campo vacío NO SHALL contar como revisado: la ausencia SHALL registrarse de forma **afirmativa**. El checklist SHALL exigir el **odontograma inicial** del paciente y ambos documentos SHALL sellarse de forma **atómica**: si falla el odontograma, la Historia SHALL seguir en `DRAFT`.

#### Scenario: Completar cuando el checklist está satisfecho
- **WHEN** un usuario solicita marcar como `COMPLETED` una Historia Clínica cuyo checklist mínimo está cubierto y cuyo paciente tiene el odontograma inicial registrado
- **THEN** el sistema sella la Historia en estado `COMPLETED` **y** el odontograma inicial, en una sola operación

#### Scenario: Rechazar el sellado con un ítem crítico faltante
- **WHEN** un usuario intenta marcar `COMPLETED` una Historia Clínica a la que le falta un ítem del checklist mínimo
- **THEN** el sistema rechaza la transición e indica qué ítems faltan

#### Scenario: Rechazar el sellado sin odontograma inicial
- **WHEN** un usuario intenta marcar `COMPLETED` una Historia Clínica cuyo paciente no tiene odontograma inicial registrado
- **THEN** el sistema rechaza la transición e indica que falta el odontograma

#### Scenario: Un fallo al sellar el odontograma deja la Historia en DRAFT
- **WHEN** el sellado del odontograma inicial falla durante la operación de completar la Historia Clínica
- **THEN** el sistema deja la Historia en `DRAFT` y el odontograma sin sellar, sin aplicar ninguno de los dos cambios

#### Scenario: Un campo de alergias vacío no equivale a "sin hallazgos"
- **WHEN** un usuario deja el campo de alergias vacío (sin afirmar "sin alergias conocidas") e intenta completar
- **THEN** el sistema rechaza el sellado porque el ítem no fue registrado afirmativamente

#### Scenario: Un campo de medicación vacío no equivale a "no toma medicación"
- **WHEN** un usuario deja la medicación sin registrar afirmativamente (ni un detalle ni "no toma medicación") e intenta completar
- **THEN** el sistema rechaza el sellado porque el ítem no fue registrado afirmativamente

#### Scenario: Rechazar una transición inválida
- **WHEN** un usuario intenta cambiar una Historia de `COMPLETED` a `DRAFT`, o volver a ejecutar el sellado sobre una ya `COMPLETED`
- **THEN** el sistema rechaza la operación y conserva el estado `COMPLETED`

#### Scenario: Las Historias selladas antes de esta regla no se ven afectadas
- **WHEN** se consulta una Historia Clínica que fue sellada antes de que el odontograma fuera obligatorio
- **THEN** el sistema la muestra como `COMPLETED` sin exigirle un odontograma retroactivamente
