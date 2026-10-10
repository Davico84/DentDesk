# Spec Delta — patients

## ADDED Requirements

### Requirement: El listado conserva la búsqueda al volver
El listado de pacientes SHALL reflejar en la URL la búsqueda, la página y si incluye a los dados de baja. Volver al listado desde la ficha o el formulario de un paciente SHALL regresar a esa misma búsqueda y página, aunque en medio se haya navegado a la Historia, el odontograma o las consultas. Sin un listado previo en la pestaña, SHALL volver al listado sin filtros.

#### Scenario: La búsqueda queda en la URL
- **WHEN** se busca "Lucía" y se pasa a la página 2
- **THEN** la URL del listado refleja la búsqueda y la página
- **AND** recargar la página muestra los mismos resultados

#### Scenario: Volver desde la ficha conserva la búsqueda
- **WHEN** desde un listado filtrado se abre un paciente, se pasa por su Historia Clínica y se vuelve al listado desde la ficha
- **THEN** el listado muestra la misma búsqueda y la misma página

#### Scenario: Sin listado previo
- **WHEN** se abre la ficha de un paciente directamente por URL y se vuelve al listado
- **THEN** el listado se muestra sin filtros, en la primera página

#### Scenario: El listado recordado no sobrevive al cierre de sesión
- **WHEN** el usuario cierra sesión y vuelve a entrar
- **THEN** volver al listado lo muestra sin la búsqueda anterior
