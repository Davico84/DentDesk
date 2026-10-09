# ADR-0020: La nota de consulta — contenido, ciclo de vida y corrección según el texto oficial

- **Estado:** Aceptado (decisión); se implementa en `update-clinical-history` (fase 2, corte A)
- **Fecha:** 2026-10-09
- **Decisores:** David Velarde Alvarado
- **Corrige:** [`ADR-0017`](0017-catalogo-odontograma-nts-188.md) **D3** (la nota final: "el cierre diario sí aplica a la nota de evolución") · [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) **D2** (fila 12.1) y **D3** (fila "Evolución estructurada SOAP")
- **Fuente:** **texto oficial**, versionado en [`docs/compliance/normas/`](../compliance/normas/README.md): NTS N°139-MINSA/2018/DGAIN (RM N°214-2018/MINSA) con su modificatoria RM N°265-2018/MINSA; D.S. N°009-2017-SA (Reglamento de la Ley N°30024, RENHICE). Leído el 2026-10-09.
- **Relacionado:** [`ADR-0011`](0011-historia-clinica-documento-inicial.md) (la Historia es el documento inicial; la evolución llega después) · [`ADR-0012`](0012-odontograma-libro-de-asientos.md) D3/D5/D7 (libro de asientos, cierre conjunto, cronología) · [`ADR-0016`](0016-responsabilidad-clinica.md) D1/D7 (autoría con colegiatura, bloqueada por `staff`)

## Contexto

`clinical-history` cubre el **documento inicial**: se sella en la admisión y después no queda ningún registro clínico posible. El roadmap preveía para la fase 2 una `ClinicalEvolutionEntry` en formato **SOAP**, con **cierre diario**, y un **odontograma inicial y final** como exigencia general. Las tres cosas venían de los análisis derivados que [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) D8 degradó, y ninguna se había contrastado con el texto.

Leído el texto oficial, ninguna de las tres sobrevive:

| Lo que creíamos | Lo que dice el texto |
|---|---|
| La evolución se estructura en **SOAP** (NTS 139 §5.2.2 · §4.2.9) | "SOAP" no aparece en la norma. **§4.2.9 trata de diagnósticos**: CIE10, codificados por quien atendió, con tipo **Presuntivo (P), Definitivo (D) o Repetitivo (R)**. El contenido de las consultas siguientes está en **§5.2.1 (1)** |
| La nota se **cierra una vez al día** | *"La frecuencia de las evoluciones se realiza, como mínimo, una vez al día"* está en **§5.2.1 (3), Formatos en Hospitalización**. En consulta externa no hay cierre diario |
| El odontograma es **inicial y final** | *"Odontograma inicial y final"* es de **§5.2.2 (12.1), Ficha del Niño**. La ficha del adolescente, joven, adulto y adulto mayor (**12.2**) pide *"Odontograma"* y *"Alta básica odontológica con fecha"* |

Y una cuarta, que no venía de los análisis sino de la lectura apurada del explore: la lista *"Las siguientes consultas deben contener"* que tomamos como contenido de la nota es de **§5.2.1 (1) 1.2, Hospitales e Institutos Especializados**. Un consultorio dental privado es **primer nivel de atención (1.1)**, donde la norma usa *"formatos por etapa de vida"*: solo el del niño (**1.1 d**) trae una lista para las consultas siguientes; adolescente (e), joven (f), adulto (g) y adulto mayor (h) fijan únicamente la primera atención.

## Decisión

### D1 — La nota de consulta es un registro propio, con el contenido de §5.2.1 (1)

Cada atención posterior a la admisión genera una **nota de consulta**, agregado propio colgado del paciente (no fila hija de la Historia). Requiere la Historia `COMPLETED` y el paciente **activo**.

Contenido mínimo: la **unión** de las listas de 1.1 d y 1.2, porque la norma no fija una lista para las consultas siguientes de un adulto en primer nivel y ninguna de las dos, sola, cubre lo que un odontólogo necesita registrar:

| Campo | 1.1 d (niño, primer nivel) | 1.2 (hospitales) | En la nota |
|---|---|---|---|
| Fecha y hora de la atención | ✅ | ✅ | obligatorio (D3) |
| Motivo de consulta | ✅ | ✅ | **obligatorio** |
| Tiempo de enfermedad | ✅ | — | opcional |
| Síntomas y signos | — | ✅ | opcional |
| Tratamiento recibido, cumplimiento y resultado | — | ✅ *"de ser el caso"* | opcional |
| Funciones biológicas | ✅ | — | opcional |
| Examen físico | ✅ | ✅ | **obligatorio**, como *evaluación odontológica* (§5.2.2 (12)) |
| Diagnóstico (CIE 10) | ✅ | ✅ | **al menos uno**, con tipo P/D/R (§4.2.9) |
| Tratamiento | ✅ | ✅ | **obligatorio** |
| Exámenes auxiliares | ✅ | — | opcional |
| Referencia si fuera el caso | ✅ | — | opcional |
| Fecha de próxima cita | ✅ | ✅ | opcional, solo fecha |
| Edad | ✅ | — | derivada de la fecha de nacimiento |
| Firma, sello y colegiatura | ✅ | ✅ | autoría por usuario; colegiatura con `staff` (D6) |

> ⚠️ La unión es una **decisión**, no una lectura: la norma no la ordena. Es el mínimo defendible ante una auditoría porque contiene todo lo que cualquiera de las dos listas aplicables podría exigir.

### D2 — Ciclo `ABIERTA → SELLADA`, sin cierre diario

Abierta es un borrador editable; sellada es inmutable. Una sola nota abierta por paciente, y las notas se registran en orden cronológico (**NTS 139 §5.3.1 (7)**, registro secuencial; misma regla de cronología que `ADR-0012` D7). Se puede **descartar solo si está vacía**: lo escrito en una atención es registro clínico aunque no se haya sellado. No hay cierre automático (ver Contexto).

### D3 — Dos tiempos: atención y escritura

La **fecha y hora de la atención** la ingresa el odontólogo (no futura, no anterior a la última nota sellada). Aparte, el sistema registra cuándo se escribió y quién. **DS 009-2017-SA art. 32** pide trazabilidad de los registros; una sola marca obligaría a mentir en una de las dos preguntas.

### D4 — Diagnóstico repetitivo exige registro previo

**§4.2.9** distingue Presuntivo, Definitivo y **Repetitivo**. Un R sin diagnóstico anterior del mismo código no tiene sentido clínico: se rechaza si el código CIE10 no figura en la Historia o en una nota sellada del paciente. La misma nota abierta no cuenta como registro previo.

### D5 — La corrección es un registro nuevo, y solo de error material

**NTS 139 §5.3.3 j**: *"Ningún dato registrado en la Historia Clínica Electrónica podrá ser eliminado, y de ser necesaria su corrección, ésta deberá realizarse como un nuevo registro, sin eliminar el que se pretende corregir, e indicando el motivo de tal corrección"*.

**DS 009-2017-SA art. 47 c**: *"Cuando el profesional de la salud que atiende detecta un error material en su registro de la atención procederá hacer la corrección como un registro nuevo, precisando la ubicación del error. Esto no aplica para los diagnósticos ni omisión de registro."*

> ⚠️ La letra **j** se deduce del orden: en el texto extraído el inciso aparece entre **i** (Información Clínica Sensible) y **k**, sin su letra. El explore lo había citado como *i*.

De ahí, cuatro reglas: la corrección indica **campo** (ubicación), **texto corregido** y **motivo**; la registra **quien selló la nota** ("su registro"); **no** se aplica a diagnósticos; **no** se aplica a un campo que estaba vacío (omisión). El original queda intacto y se muestra junto a sus correcciones.

**Art. 46** cierra la puerta a la otra lectura: *"No se puede modificar la información clínica […] de un registro hecho anteriormente aduciendo que no refleja la realidad actual."* Por eso una alergia que aparece hoy **no es una corrección** de la Historia: es un hecho de la consulta de hoy (D6).

### D6 — Antecedentes nuevos como asientos de alta y baja

En la nota se registran **altas** y **bajas** de antecedentes en tres categorías: **alergias y reacciones adversas** (**§4.2.10** obliga a registrar las sospechas de reacción adversa), **antecedentes patológicos personales** y **medicación actual**. Es el patrón de libro de asientos de `ADR-0012` D3: el hecho clínico es el asiento, lo vigente se calcula. Una baja no borra el alta.

En consecuencia, la anamnesis de la ficha del paciente (`patients`) se **congela** desde que existe la Historia: si siguiera editable, habría dos lugares para el mismo dato y uno de ellos sin autoría clínica. La vista de antecedentes vigentes la muestra como *registrada al dar de alta, sin autoría clínica*.

### D7 — Autoría por usuario mientras no exista `staff`

Cualquier usuario de la clínica escribe y sella notas; la autoría es el usuario. `ADR-0016` D1 (colegiatura fotografiada) y D7 (solo el habilitado ejerce) siguen bloqueados por `staff`. Riesgo aceptado y explícito: **NTS 139 §5.2.1 (1)** pide *"firma, sello y colegiatura"*, y hoy el sistema solo puede dar la primera parte.

## Consecuencias

- ✅ Toda atención posterior a la admisión tiene registro, con el contenido que la norma pide y sin forma de reescribirlo.
- ✅ La corrección cumple §5.3.3 j y art. 47 c por construcción: no existe una operación que modifique el original.
- ✅ Los antecedentes nuevos son alertables (listas, no texto) y fechados por la consulta que los registró.
- ⚠️ Las fechas de una nota sellada no se pueden corregir (la corrección es solo de campos de texto).
- ⚠️ El texto de la Historia y la anamnesis congelada de la ficha no se pueden dar de baja: no son ítems. La vista los muestra con su origen y fecha.
- ⚠️ Cada nota escrita antes de `staff` queda sin colegiatura. No se reescribe después.
- ⚠️ Corte B (odontograma de evolución, `update-dental-chart`) debe sellarse **junto con** la nota (`ADR-0012` D5): el sellado de la nota queda como punto único al que B se suma.

## Alternativas descartadas

- **Estructura SOAP.** Descartada: la norma no la nombra. Imponerla agregaría una forma que ningún auditor va a pedir y dejaría afuera campos que sí pide (tratamiento recibido, próxima cita, referencia).
- **Cierre diario automático de la nota.** Descartada: es una regla de hospitalización. En consulta, una nota olvidada se resuelve bloqueando la siguiente hasta sellarla o descartarla.
- **Usar solo la lista de 1.2.** Descartada: es de hospitales e institutos; citarla como obligación de un consultorio sería repetir el error de citar un texto que no aplica.
- **Usar solo la lista de 1.1 d.** Descartada: es del formato del niño, y no tiene síntomas y signos ni tratamiento recibido, que en la consulta odontológica de un adulto son centrales.
- **Una sola tabla de diagnósticos para Historia y notas.** Descartada: mezcla dos ciclos de vida cuya protección (triggers de sello) depende del padre.
- **Corregir editando la nota con historial de versiones.** Descartada: §5.3.3 j pide un *registro nuevo*, no una versión del mismo registro.
- **Que la alergia nueva corrija la Historia.** Descartada por el art. 46: lo registrado antes no se modifica porque haya cambiado la realidad.
