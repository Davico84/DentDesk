# Spec Delta — patients

## ADDED Requirements

### Requirement: La anamnesis de la ficha se congela al existir la Historia Clínica
Desde que el paciente tiene Historia Clínica, en cualquier estado, el sistema SHALL rechazar todo cambio a las **alergias**, **condiciones/patologías** y **medicación actual** de su ficha: lo nuevo se registra en la consulta, como antecedente de la nota. Los demás datos de la ficha SHALL seguir editables, y la anamnesis congelada SHALL seguir legible.

#### Scenario: La anamnesis se edita mientras no hay Historia Clínica
- **WHEN** se modifican las alergias de un paciente que aún no tiene Historia Clínica
- **THEN** los cambios quedan guardados

#### Scenario: Rechazar cambios a la anamnesis congelada
- **WHEN** se intenta modificar las alergias, condiciones o medicación de un paciente que ya tiene Historia Clínica
- **THEN** el sistema rechaza el cambio indicando que los antecedentes nuevos se registran en la consulta
- **AND** no modifica nada de la ficha

#### Scenario: El resto de la ficha sigue editable con la anamnesis congelada
- **WHEN** se edita el teléfono de un paciente con Historia Clínica, enviando su anamnesis sin cambios
- **THEN** el cambio de teléfono queda guardado

#### Scenario: La anamnesis congelada sigue legible
- **WHEN** se consulta la ficha de un paciente con Historia Clínica
- **THEN** su anamnesis se muestra en modo lectura, indicando que no admite cambios
