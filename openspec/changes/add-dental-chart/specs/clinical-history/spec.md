## MODIFIED Requirements

### Requirement: Sellado a COMPLETED con checklist mínimo afirmativo

La transición de `DRAFT` a `COMPLETED` SHALL ser una acción manual y explícita que valida un **checklist mínimo** de completitud clínica antes de sellar, y SHALL ser **unidireccional**: una Historia `COMPLETED` no vuelve a `DRAFT` ni se vuelve a sellar. En las secciones donde "ninguno" es información clínica válida (**alergias, medicación, antecedentes familiares**), un campo vacío NO SHALL contar como revisado: la ausencia debe registrarse de forma **afirmativa** ("sin alergias conocidas" / "no toma medicación" / "sin antecedentes familiares"), distinta de "no llenado". El mismo patrón (revisado + detalle opcional) aplica por igual a esas secciones.

El checklist SHALL exigir además que el paciente tenga su **odontograma inicial** registrado, porque es anexo obligatorio de la historia clínica. El sellado de ambos documentos SHALL ocurrir de forma **atómica**: la Historia Clínica ya NO SHALL sellarse por sí sola. Si el sellado del odontograma falla, la Historia SHALL permanecer en `DRAFT` — ninguno de los dos puede quedar sellado sin el otro, ni modificarse después de forma independiente.

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
