# ADR-0018: Realineamiento del cumplimiento con el texto oficial de NTS 139 y del Código de Ética del COP

- **Estado:** Aceptado (decisión); **pendiente de implementar** — abre un gap 🔴 sobre una capacidad **ya construida**
- **Fecha:** 2026-09-23
- **Decisores:** David Velarde Alvarado
- **Supersede:** [`ADR-0014`](0014-ciclo-de-vida-del-documento-clinico.md) **D5**, **D6** y **D7** (el plazo de conservación) · [`ADR-0017`](0017-catalogo-odontograma-nts-188.md) **D8** (parcialmente: el IHO) · refina el fundamento normativo de [`ADR-0016`](0016-responsabilidad-clinica.md) **D1** y **D7**
- 🔴 **Corregido por [`ADR-0020`](0020-nota-de-consulta.md)** (2026-10-09), contra el mismo texto oficial: en **D3**, la fila *"Evolución estructurada SOAP"* cita §5.2.2 · §4.2.9, pero "SOAP" no aparece en la norma y **§4.2.9 es sobre diagnósticos** (CIE10 + P/D/R); el contenido de la nota sale de §5.2.1 (1). En **D2**, *"Odontograma inicial y final"* es solo de **12.1, Ficha del Niño**; la 12.2 (adulto) pide *"Odontograma"* y *"Alta básica odontológica con fecha"*.
- **Fuente:** **texto oficial** de la NTS N°139-MINSA/2018/DGAIN (RM N°214-2018/MINSA, **con** su modificatoria RM N°265-2018/MINSA) y del **Código de Ética y Deontología del COP** (aprobado el 16.02.2024, Resolución N°01-02.SOCN.COP.2024, 104 artículos)

## Contexto

[`ADR-0017`](0017-catalogo-odontograma-nts-188.md) dejó escrita una regla y la aplicó a una sola norma: *"un análisis derivado de una norma no es la norma"*. Este ADR es **la misma auditoría sobre las otras dos** normas que el proyecto cita: NTS 139 (historia clínica) y el Código de Ética del COP.

El resultado es peor que en el odontograma. Allí fallaron tres detalles de color y una regla de generación. Acá, **ninguna de las citas era correcta**:

| Norma | Citas en `regulatory-matrix.md` | Correctas |
|---|---|---|
| NTS 188 | 12 | 12 ✅ *(ya realineadas por `ADR-0017`)* |
| **NTS 139** | 7 numerales distintos | **0** — ninguno de los § citados existe en la norma |
| **COP** | 4 artículos | **0** — los cuatro apuntan a artículos que dicen otra cosa |

Y un error de **plazo legal**, no de referencia: la conservación son **20 años**, no 15. Ese número está escrito en un ADR aceptado, en el proposal de un change activo y en cuatro filas de la matriz.

⚠️ **La causa raíz no es el descuido de una cita.** Los documentos `docs/compliance/nts-139-history-clinical.md` y `docs/compliance/cop-code-of-ethics.md` **son** análisis derivados generados sin el texto delante, y la matriz los declara *"el análisis por norma"* — es decir, los puso como fuente de contenido normativo. Mientras conserven esa autoridad, el error se vuelve a cometer. D8 cierra eso.

## Decisión

### D1 — La conservación es de **20 años**, no de 15

**El número estaba mal en el ADR aceptado.** `ADR-0014` D5 fijó 15 años. El texto oficial da tres plazos distintos y 15 es **el tramo pasivo**, no el total:

| Tramo | § NTS 139 | Plazo |
|---|---|---|
| Archivo **activo** | **5.3.1 (4) k** | 5 años |
| Archivo **pasivo** | **5.3.1 (4) l** | 15 años más |
| **Conservación total** desde la última atención | **5.3.1 (6) e** | **20 años** |
| Cáncer de origen ocupacional | **5.3.1 (6) e** | **40 años** |

Confirmado por una **segunda fuente oficial independiente**: **COP art. 33** exige conservar la historia clínica *"un mínimo de veinte (20) años"*. Dos normas distintas coinciden en 20; ninguna dice 15 como total.

**Qué cambia y qué no:**

- **Cambia el plazo total**: 20 años desde `ultimaAtencion`, y **40** si hay diagnóstico de cáncer de origen ocupacional. Ese segundo caso es nuevo: no estaba en ningún artefacto.
- **Sobrevive intacta la definición de `ultimaAtencion`** de `ADR-0014` D5 (el máximo de *cualquier* registro clínico, sellado o no). Ese razonamiento era correcto y el error asimétrico que lo motiva —conservar de más antes que destruir de menos— ahora juega a favor: el plazo real es mayor que el que teníamos escrito.
- **Sobrevive `ADR-0014` D6**: el corte activo/pasivo en 5 años era correcto. Lo que estaba mal implícito era el final del tramo pasivo.
- **Sobrevive `ADR-0014` D7** (no se construye la destrucción) con más margen todavía: el escenario se aleja cinco años más.

⚠️ **Por qué este error era el más caro de los encontrados.** Los demás son referencias equivocadas a la norma correcta: molestan a un auditor, no dañan un dato. Este es una **política de retención más corta que la legal**. Si se hubiera implementado como precondición de purga, el sistema habría autorizado destruir expedientes con protección legal vigente durante cinco años — y un expediente destruido no se recupera corrigiendo un ADR.

### D2 — El **Índice de Higiene Oral sí es obligatorio** — pero no en el odontograma

`ADR-0017` **D8** concluyó que el IHO y el registro periodontal *"no aparecen en NTS 188"* y quedaban fuera de alcance. **La primera mitad sigue en pie; la segunda era una inferencia.** El IHO no está en NTS 188 porque **no pertenece al odontograma** — pertenece a la Ficha Odonto-Estomatológica, y **NTS 139 la exige**:

**§ 5.2.2, numeral 12 — Fichas Odonto-Estomatológicas:**

| Sub-numeral | Exige | Artefacto que lo cubriría |
|---|---|---|
| **12.1** | **Odontograma inicial y final** | `dental-chart` — el inicial está propuesto; **el final no existe en ningún artefacto** |
| **12.2** | **Índice de higiene oral simplificado** | ninguno |
| **12.2** | **Índice de Placa Blanda y Placa Calcificada** | ninguno |
| **12.2** | **Alta básica odontológica con fecha** | ninguno |

**Consecuencia: es un gap 🔴, no 🚧.** Los tres campos pertenecen a la **Ficha Odonto-Estomatológica**, cuyo hogar natural es `clinical-history` — una capacidad **construida, archivada y en uso** (archivada el 2026-07-30). No es "algo que todavía no hacemos": es una ficha normativa que el sistema ya pretende cubrir y cubre incompleta.

**Qué se corrige de `ADR-0017` D8, con precisión:**

- ✅ **Se mantiene**: el IHO **no va en el odontograma**. Meterlo ahí sería un error de modelado, y la exclusión de `add-dental-chart` es correcta.
- ✅ **Se mantiene completo el registro periodontal**: sondaje por sitio y compromiso de furca no aparecen **ni en NTS 188 ni en el numeral 12 de NTS 139**. El periodontograma sigue siendo documento aparte, fuera de alcance por decisión.
- 🔴 **Se corrige**: la frase *"si es obligatorio, será por NTS 139"* se escribió como hipótesis y **es un hecho**. Deja de ser una nota al pie y pasa a ser una fila de la matriz con estado 🔴.
- ⚠️ **Se descubre de paso el odontograma final** (12.1). `add-dental-chart` cubre el inicial; el final no está ni en sus Non-goals — no se excluyó, se pasó por alto. Cae en `update-dental-chart`, junto con la evolución.

**No se resuelve acá dónde viven los tres campos.** Es un `update-clinical-history` con su propio diseño: el IHO simplificado tiene índice por pieza indicadora y valor agregado, que es un modelo de datos, no una columna. Este ADR **abre el gap y lo nombra**; no lo cierra.

### D3 — Los numerales de NTS 139 citados en la matriz **no existen**: reemplazo uno a uno

Los siete § que la matriz citaba venían del análisis derivado. Ninguno corresponde a la estructura real de la norma: **no hay 6.6, no hay 5.4, no hay 6.1.1, no hay 6.1.4.**

| Requisito | § citado (**inventado**) | § **real** |
|---|---|---|
| Filiación completa obligatoria | ~~6.1.1 a)~~ | **5.3.1 (1) b** · **5.3.3 d** |
| Identificador único del expediente | ~~6.1.1 a)~~ | **5.3.3 e** — ver D7: la norma **separa** identificación de atención |
| Registro secuencial y cronológico | ~~5.4.1 f)~~ | **5.3.1 (7)** · **5.3.3 h** |
| Prohibición de enmienda / destrucción de folios | ~~5.4.1 i)~~ | **5.3.1 (8) b** · **5.3.3 j** |
| Evolución estructurada SOAP | ~~6.1.4~~ | **5.2.2** (estructura de la HC) · **4.2.9** |
| Conservación y archivo | ~~6.6.1 / 6.6.2~~ | **5.3.1 (4) k/l** · **5.3.1 (6) e** |
| Destrucción con acta del Comité | ~~6.6.1 e)~~ | **5.3.1 (6) e** · **5.3.2 (1) c** |
| Definiciones operativas usadas en el modelo | — | **4.2.1** · **4.2.23** · **4.3.2** |

**Un caso peor que una cita equivocada: [`ADR-0015`](0015-identidad-del-paciente.md).** Su decisión central es *cuándo* se exige la filiación completa, y la justificó así:

> *"El análisis normativo del repo lo sitúa ahí con precisión: «el formulario debe obligar a completar la totalidad de los campos demográficos **antes de permitir la apertura de la historia clínica**»"*

Esa frase entre comillas **no es texto de la norma**. Es el apartado *"Impacto Funcional"* de `nts-139-history-clinical.md` — una **inferencia del análisis derivado, presentada como cita normativa** y usada para elegir entre tres momentos posibles. El requisito existe; **el momento no está verificado**.

`ADR-0015` **se mantiene**: sus dos argumentos de diseño —no en el alta, porque frena la recepción y excluye al recién nacido sin DNI; no en el sellado, porque una historia abierta ya es un documento en curso— se sostienen sin ninguna cita. Lo que se retira es la afirmación de que la norma señala ese punto. Pasa de *mandato textual* a **la lectura más conservadora compatible con la norma**, que es una base honesta y suficiente para la misma decisión.

⚠️ **El requisito de fondo era correcto en los siete casos.** Lo inventado fue la referencia, no la obligación: la norma sí exige filiación completa, sí prohíbe la enmienda, sí manda registro cronológico. Por eso el error sobrevivió a dos revisiones — **cada fila se leía verdadera**. Eso es precisamente lo que la hace peligrosa ante una auditoría: un requisito cierto apoyado en un artículo inexistente se lee como una cita fabricada para justificar una decisión ya tomada.

### D4 — Los cuatro artículos del COP citados apuntan a otra cosa

| Requisito en la matriz | Artículo citado | Qué dice **realmente** ese artículo | Artículo **correcto** |
|---|---|---|---|
| Secreto profesional | ~~Arts. 31–36~~ | otra materia | **Arts. 74–77** |
| Autoría por colegiatura | ~~Art. 42~~ | **interconsulta** | ver **D6** — no hay un artículo de "autoría"; el fundamento es otro |
| Firma digital del cierre | ~~Arts. 52–53~~ | **competencia desleal** (52) y **atención al colega** (53) | **ninguno** — el COP no menciona firma digital |
| Integridad: prohibido alterar registros | ~~Art. 54~~ | **prohibición de comisiones** | **Art. 33** (conservación) + NTS 139 **5.3.1 (8) b** |
| Conservación de la historia clínica | — | — | **Art. 33** — mínimo 20 años (ver D1) |
| Bancos de datos de salud electrónicos | — | — | **Art. 78** (ver D5) |

**Dos hallazgos que cambian decisiones, no solo referencias:**

1. **El COP no exige firma digital.** La matriz presentaba la firma como obligación COP + Ley 27269. El COP **no la menciona en ningún artículo**. Eso no elimina el requisito —la Ley 27269 es la fuente real y sigue sin verificarse— pero **le quita una de sus dos patas**. `ADR-0014` D2 decidió posponer la firma con un punto de extensión; esa decisión **se refuerza**: el sustento normativo es más débil de lo que la matriz afirmaba.
2. **No existe un artículo del COP que ordene "autoría por colegiatura"** en los términos en que `ADR-0016` D1 lo citaba. Ver D6.

⚠️ **Las filas de la matriz que citan el COP y no aparecen en esta tabla quedan marcadas ⚠️, no corregidas.** No se les asigna un artículo nuevo: verificarlo exige releer el texto, y **inventar un reemplazo plausible sería repetir el error exacto que este ADR corrige**. Un `⚠️ sin verificar` es información honesta; una cita inventada no.

### D5 — COP art. 78: los **bancos de datos de salud electrónicos**

El artículo prohíbe al cirujano dentista **cooperar en la creación o comercialización de bancos de datos de salud electrónicos** que puedan comprometer la confidencialidad del paciente.

**Esto no está en ningún artefacto del proyecto, y es el hallazgo más incómodo de esta auditoría**, porque Dentdesk **es** una base de datos de salud electrónica multi-tenant, comercializada por suscripción.

**Lo que sí se decide acá** — la lectura defendible del artículo es que apunta a la **agregación y explotación** de datos clínicos más allá de la relación clínica que los originó, no a que una clínica lleve su historia en software. De ahí salen tres reglas de producto, y las tres **ya son coherentes con decisiones tomadas**:

| Regla | Ya cubierta por |
|---|---|
| Los datos clínicos **no cruzan el límite del tenant** para ningún propósito, incluido el análisis agregado | [`ADR-0004`](0004-el-paciente-pertenece-a-una-clinica.md) + RLS (`multi-tenancy`, construida y testeada) |
| El operador del SaaS **no es un destinatario** de los datos clínicos | pendiente — `ADR-0016` **D5** (cifrado de columnas clínicas) es el mecanismo |
| Ninguna funcionalidad de producto **monetiza el dato clínico** (ni benchmarking entre clínicas, ni venta de estadísticas, ni entrenamiento de modelos) | ⚠️ **sin decisión escrita** — hasta hoy simplemente no existía la funcionalidad |

⚠️ **Lo que NO se decide acá, explícitamente:** si la operación de un SaaS clínico multi-tenant en Perú requiere algo más que esto —inscripción del banco de datos ante la Autoridad Nacional de Protección de Datos Personales, cláusulas de encargo de tratamiento con cada clínica, o un rol formal de *encargado* frente a *titular*— es una **pregunta legal, no de arquitectura**, y cruza con la Ley 29733, que el proyecto **todavía no ha verificado contra su texto oficial**. Se registra como riesgo de producto con dueño pendiente. Afirmar acá que Dentdesk cumple el art. 78 sería exactamente el tipo de sobreafirmación que este ADR existe para corregir.

### D6 — La credencial clínica es la **habilitación**, no la colegiatura

`ADR-0016` **D7** decidió que la capacidad clínica es una **credencial verificable** y no un rol. **Esa decisión es correcta y se mantiene entera** — es la mejor del ADR. Lo que estaba mal era *cuál* credencial y en qué artículo se apoyaba.

| | `ADR-0016` D7 decía | El texto del COP implica |
|---|---|---|
| Credencial | **colegiatura** | **habilitación** |
| Naturaleza | hecho permanente | **estado mutable** |
| Fundamento | ~~art. 42~~ (es interconsulta) | **art. 65** — la habilitación depende del cumplimiento de la cuota ordinaria |

**Colegiatura y habilitación no son lo mismo:**

- La **colegiatura** se obtiene una vez y no caduca. Es un número.
- La **habilitación** es la colegiatura **vigente**: se pierde por no pagar la cuota (art. 65) y por sanción disciplinaria. Un odontólogo colegiado **inhabilitado no puede ejercer legalmente**.

**Por qué esto mejora la decisión en vez de romperla.** `ADR-0016` D7 argumentó que la credencial es superior al rol porque *"la suspensión revoca sola"*. Con la colegiatura modelada como número permanente, **eso no era cierto**: nada revocaba nada, porque el dato nunca cambiaba. Con la habilitación como estado, el argumento original **por fin funciona**.

**Qué cambia en el modelo de `staff`, concretamente:**

- El perfil profesional guarda **número de colegiatura** (identificador estable, para imprimir y para trazar autoría) **y estado de habilitación** (mutable, con vigencia). Son dos datos, no uno.
- La puerta de los actos clínicos la abre **la habilitación vigente**, no la existencia del número.
- 🔴 **La autoría sellada copia ambos.** `ADR-0016` D1 decidió **copiar** la colegiatura al firmar en vez de referenciarla — decisión correcta y aquí crítica: el acto clínico debe conservar que el autor **estaba habilitado en ese momento**, porque una inhabilitación posterior no invalida retroactivamente lo que firmó estando habilitado. Si se referenciara el perfil vivo, un cambio de estado reescribiría la historia de todos sus registros.
- ⚠️ **La habilitación sigue siendo autodeclarada** en esta etapa, igual que la colegiatura en `ADR-0016` D7. El COP publica un registro de habilitados; verificar contra él es una mejora posterior que este modelo admite sin cambios de forma. **Se declara la limitación en vez de disimularla**: hoy el sistema cumple el art. 65 *por construcción del modelo*, no *por verificación del hecho*.

### D7 — Cinco exigencias de NTS 139 que **no están en ningún artefacto**

No son correcciones: son requisitos que la norma impone y que ni la matriz, ni los ADR, ni los changes mencionan. Se registran para que existan como filas con dueño.

| # | Exigencia | § | Por qué importa |
|---|---|---|---|
| 1 | **La identificación del paciente se registra por separado de la atención** | **5.3.3 e** | Es una decisión de modelado, no de formulario: hoy `patients` y `clinical-history` ya están separados, así que probablemente **se cumple por accidente**. Hay que verificarlo contra el § antes de ponerlo ✅. |
| 2 | **El número de historia clínica es el DNI del paciente** | **5.3.3 e** | ⚠️ **Choca con una decisión tomada.** [`ADR-0009`](0009-documento-unico-por-clinica.md) hace el documento único **por clínica**, y el modelo multi-tenant permite el mismo DNI en dos clínicas — correcto como negocio. Pero si el número de HC **es** el DNI, entonces el identificador del expediente no es global, y un paciente sin DNI (extranjero con CE, recién nacido) necesita regla propia. Necesita su propio ADR. |
| 3 | **Acreditación ante el RENHICE** | **5.3.3 l** | El Registro Nacional de Historias Clínicas Electrónicas. Condiciona que la HC electrónica tenga valor legal pleno. **Es un trámite institucional del titular, no código** — pero si nunca se hace, la premisa del producto se debilita. Dueño: el usuario, no el repo. |
| 4 | **Consentimiento explícito para el tratamiento de datos personales** (Anexo 11) | **5.3.3** + Anexo 11 | ⚠️ Distinto del consentimiento **clínico** de [`ADR-0007`](0007-tutor-menores.md) y `ADR-0016` D6. Son dos consentimientos con textos, momentos y fundamentos legales distintos; el proyecto solo modela uno. |
| 5 | **Copia de la historia clínica al paciente en 5 días** | **5.3.3 r** | Plazo duro. Conecta con `ADR-0014` **D3** (exportación formal del expediente), que hasta ahora no tenía urgencia ni plazo asociado. Le pone ambos. |

### D8 — Los análisis derivados de `docs/compliance/` **pierden autoridad normativa**

La causa raíz. `regulatory-matrix.md` declara: *"El análisis por norma vive en los documentos hermanos de esta carpeta"*. Eso convirtió cinco documentos generados **sin el texto delante** en la fuente de contenido normativo del proyecto — y de ahí salieron los § de D3 y los artículos de D4.

Esos documentos contienen, además de las citas falsas, requisitos no funcionales que **ninguna de las dos normas menciona**: `TLS 1.3`, `AES-256`, `422 Unprocessable Entity`, `access_log`, `AsyncAPI`, un *"congelamiento atómico a las 23:59:59"* y códigos de requisito inventados (`REQ-ANA-02`). No son interpretaciones discutibles: son afirmaciones normativas sin norma.

**Decisión — no se borran, se degradan.** Se les pone una advertencia en la cabecera que dice qué son y qué no, y **dejan de ser citables como fuente**:

| | Antes | Ahora |
|---|---|---|
| **Qué son** | *"el análisis por norma"* | **análisis derivado, no verificado contra el texto oficial** |
| **Se puede citar en un ADR** | sí, y se citó | **no** |
| **Para qué sirven** | fuente de requisitos | inventario de **hipótesis a verificar**, e impacto técnico ya razonado |

**Por qué degradar y no borrar.** Su traducción a impacto técnico —qué toca en dominio, base de datos, API, testing— es trabajo real y reutilizable; en NTS 188 el espíritu estaba bien traducido y solo falló el detalle. Borrarlos perdería eso y, peor, **borraría la evidencia de qué creíamos y por qué**. Un error documentado enseña; un error desaparecido se repite.

**Reverificabilidad — el hueco que queda abierto.** Ni el PDF de NTS 139 ni el del COP están versionados en el repo, así que **las citas de este ADR no se pueden re-verificar desde el repositorio**. Se decide:

- **No versionar los PDF.** Son documentos oficiales de terceros con licencia no analizada, y pesan.
- **Sí registrar la procedencia**: norma, instrumento de aprobación, fecha de consulta y qué numerales se leyeron. Es lo que hace la cabecera de este ADR y de `ADR-0017`.
- ⚠️ **Limitación declarada**: la verificación descansa en la lectura de la sesión del **2026-09-23**, no en un artefacto del repo. Quien quiera auditarla necesita el texto oficial a mano. **Decirlo es parte de la corrección** — un ADR que afirma haber verificado sin dejar rastro de cómo es, otra vez, el mismo modo de falla.

## Consecuencias

- 🔴 **Aparece un gap sobre capacidad construida**: la Ficha Odonto-Estomatológica de `clinical-history` está incompleta (D2). Es el segundo 🔴 del proyecto, junto al secreto profesional de `ADR-0016` D2/D3.
- 🔴 **El plazo de retención escrito era más corto que el legal** (D1). Corregido antes de existir como código — el único punto de esta auditoría que podía haber destruido datos.
- ⚠️ **`add-dental-chart` necesita dos toques menores, no un realineamiento**: su Non-goal de retención dice "15 años" y su Non-goal del IHO dice *"si resulta obligatorio será por NTS 139"* — ahora es un hecho, y el **odontograma final** (12.1) hay que nombrarlo como excluido en vez de omitido. **Ninguno de los dos bloquea la implementación**: no tocan el modelo de datos, el catálogo ni el sellado.
- ⚠️ **Dos decisiones ganan fundamento en vez de perderlo**: `ADR-0016` D7 (la habilitación hace cierto su propio argumento, D6) y `ADR-0014` D2 (la firma digital tenía un sustento más débil de lo declarado, D4).
- ⚠️ **[`ADR-0015`](0015-identidad-del-paciente.md) conserva su decisión con otro apoyo** (D3): el *momento* en que se exige la filiación dejó de ser un mandato textual y pasó a ser la lectura más conservadora compatible. No cambia qué se implementa.
- ⚠️ **Tres requisitos nuevos necesitan ADR propio**: el número de HC = DNI contra `ADR-0009` (D7.2), el consentimiento de datos personales como documento distinto del clínico (D7.4), y el plazo de 5 días sobre la exportación de `ADR-0014` D3 (D7.5).
- ⚠️ **`staff` cambia de forma antes de proponerse**: el perfil profesional guarda dos datos (número + estado de habilitación), no uno (D6). Llega **a tiempo**: `staff` no está propuesta todavía.
- ⚠️ **Queda pendiente Ley 29733** y su D.S. 003-2013-JUS, ahora con más carga: sostiene `ADR-0016` D4/D5, el consentimiento de D7.4 y la parte abierta del art. 78 (D5). **Es la siguiente norma a conseguir.**
- ✅ **Ninguna capacidad construida cambia de comportamiento por este ADR.** Todo lo corregido era documentación, decisión pendiente o requisito no cubierto.

## Alternativas descartadas

- **Corregir las citas en silencio, sin ADR.** Descartada: el plazo de 20 años y el gap del IHO **cambian decisiones aceptadas**, y `ADR-0014` D5 quedaría contradicho por la matriz sin que nada explique cuál gana. La matriz enlaza; no decide.
- **Borrar los análisis derivados de `docs/compliance/`.** Descartada (D8): perdería la traducción a impacto técnico, que es válida, y la evidencia del error. Degradarlos con advertencia conserva ambas y quita el riesgo.
- **Reescribir los cinco análisis contra el texto oficial ahora.** Descartada por alcance, no por valor: son ~90 KB de documento y solo dos normas están verificadas. Reescribir las de NTS 139 y COP dejaría las otras tres con la misma autoridad falsa, que es el problema. Se hará **norma por norma, cuando cada texto oficial esté a mano** — y para NTS 188 el trabajo ya vive en `ADR-0017`.
- **Asignar un artículo COP plausible a las filas no verificadas** (D4). Descartada, y es la más importante: **sería repetir el error exacto que este ADR corrige.** Un `⚠️ sin verificar` es información; una cita verosímil e inventada es peor que no tener ninguna, porque nadie la vuelve a revisar.
- **Meter el IHO en `add-dental-chart`** ya que el change está abierto (D2). Descartada: el IHO **no pertenece al odontograma** —eso es lo único que `ADR-0017` D8 acertó— y aprovechar un change abierto para colgarle un requisito de otra ficha rompería el corte por capacidad. Va a `update-clinical-history`.
- **Bloquear `staff` y `dental-chart` hasta tener la Ley 29733 verificada.** Descartada: ninguna de las dos capacidades depende de esa norma para su modelo. `ADR-0016` D4/D5 ya están decididos y son implementables; si el texto los corrige, corrige una implementación pendiente, no datos guardados.

## Lección registrada

`ADR-0017` cerró con la regla. Esta auditoría muestra **el modo de falla que la hace necesaria**, y es distinto del que se registró allí.

En el odontograma el error era **visiblemente raro**: decir que la movilidad va en azul cuando el texto dice rojo es una afirmación concreta y falsable, y por eso la tarea bloqueante la atrapó.

Acá el error era **invisible, porque cada fila se leía verdadera**. La norma sí exige filiación completa; sí prohíbe la enmienda; sí manda registro cronológico. Los siete requisitos eran correctos. **Lo único falso era el número del artículo** — el dato que nadie relee porque parece el más objetivo de la fila.

De ahí dos reglas que van más allá de *"citá el texto oficial"*:

1. **Un requisito correcto no valida su cita.** Son dos afirmaciones independientes y se verifican por separado. Una fila de la matriz puede estar bien en la columna *Requisito* y fabricada en la columna *§*.
2. **Un número no es más confiable por ser específico.** `6.1.1 a)` se lee más verificado que *"la norma exige filiación"*, y era lo contrario: la prosa venía del espíritu de la norma, el número no venía de ninguna parte. **La precisión aparente es un riesgo, no una garantía.**

Y una tercera, sobre el proceso: **la fuente de un ADR se declara en su cabecera y se audita, no se hereda.** `ADR-0014` y `ADR-0016` citaron artículos que ningún humano ni proceso verificó, en un proyecto que ya tenía la regla escrita — porque la regla vigilaba los ADR **nuevos** y estos dos ya estaban aceptados. **Una regla que solo se aplica hacia adelante deja atrás todo lo que la motivó.**
