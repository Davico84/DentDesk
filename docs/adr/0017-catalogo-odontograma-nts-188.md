# ADR-0017: Catálogo y modelo del odontograma según el texto de NTS 188

- **Estado:** Aceptado (decisión); **pendiente de implementar** — cierra la tarea bloqueante `0.1` de `add-dental-chart`
- **Fecha:** 2026-08-03
- **Decisores:** David Velarde Alvarado
- **Supersede:** [`ADR-0012`](0012-odontograma-libro-de-asientos.md) **D2** (regla de contagio) y **D4** (bloque diario) · [`ADR-0013`](0013-odontograma-nts-188.md) **D3** (colores del catálogo)
- 🔴 **Corregido por [`ADR-0020`](0020-nota-de-consulta.md)** (2026-10-09): la nota final de **D3** (*"el cierre diario sí aplica a la nota de evolución"*) es falsa. "Una vez al día" es NTS 139 §5.2.1 (3), **hospitalización**; la nota de consulta no tiene cierre diario. D3 en sí (odontograma por evento) no cambia.
- **Fuente:** **texto oficial** de la NTS N°188-MINSA/DGIESP-2022 (RM N°559-2022/MINSA), numerales 5.4–5.18, 6.1.1–6.1.38 y Anexo. ✅ **Ahora versionado** en [`docs/compliance/normas/nts-188-odontograma-rm-559-2022-ESCANEADA.pdf`](../compliance/normas/) — es un escaneo sin capa de texto; se lee extrayendo las imágenes de página (ver el README de esa carpeta)
- ⚠️ **Re-verificación parcial del 2026-09-23** (a raíz de una revisión externa del change): **`5.2.23` es un typo DE LA NORMA**, no de este ADR — la página 14 del texto oficial dice literalmente *"5.2.23 PIEZA DENTARIA EN ERUPCIÓN"* entre 6.1.22 y 6.1.24. Confirmados además **6.1.24 extruida** y **6.1.25 intruida**, ambas en **azul** (D7 acertó), **6.1.29–31** prótesis fija / completa / **parcial removible**, **6.1.32** remanente radicular, **6.1.33–34** restauraciones, **6.1.35** sellantes, **6.1.36** superficie desgastada (`DES`), **6.1.37** tratamiento de conducto. 🔴 **Falta re-verificar el resto entrada por entrada** — ver la nota al final de D7

## Contexto

`ADR-0012` y `ADR-0013` se apoyaron en **análisis derivados** de la norma, no en su texto. Ambos lo declararon explícitamente: *"las afirmaciones normativas están pendientes de validación documental"*, y `add-dental-chart` puso la validación del catálogo como tarea **bloqueante previa a la migración** (`0.1`).

Al llegar el texto oficial, esa tarea hizo exactamente lo que debía: **encontró tres decisiones equivocadas y cuatro huecos estructurales, antes de escribir una línea de código.**

Dos de los errores venían de haber tratado un análisis derivado como si fuera la norma — incluida una Open Question que se cerró como *"confirmada"* sin que el texto la confirmara. La lección queda registrada al final.

## Decisión

### D1 — El color lo determina **el estado del propio hallazgo**, en tres comportamientos

El texto es explícito y no admite el matiz que habíamos deducido:

> **5.13**: *"El color **azul** indica **un hallazgo clínico** que se encuentra en buen estado y el color **rojo** indica **un hallazgo clínico** en mal estado, temporal o características patológicas."*

Cada tipo declara **uno** de tres comportamientos:

| Comportamiento | Cuándo | Ejemplos |
|---|---|---|
| **`SIEMPRE_AZUL`** | característica no patológica, sin estado que evaluar | macrodoncia, diastema, ectópica, giroversión, ausente, erupción |
| **`SIEMPRE_ROJO`** | patología, **o tratamiento temporal** | caries, fractura, desgaste, movilidad, corona temporal, restauración temporal |
| **`SEGUN_ESTADO`** | restauraciones y aparatos que pueden estar bien o mal | corona, restauración definitiva, sellante, implante, prótesis, ortodoncia |

**"Temporal" no necesita un eje propio.** Es un tipo distinto (`CT` no es `CM`), y ese tipo simplemente declara `SIEMPRE_ROJO`. Modelarlo como una dimensión aparte habría sido más complejo sin ganar nada.

Solo los `SEGUN_ESTADO` llevan el campo de **estado de conservación**, y el sistema lo exige en esos y lo rechaza en los demás.

### D2 — La **regla de contagio no existe**: se elimina (supersede `ADR-0012` D2)

`ADR-0012` D2 dedujo que *"una pieza con cualquier hallazgo que requiere intervención pinta todos sus marcadores en rojo"*, y `ADR-0013` la dio por confirmada citando un `UP-004` del análisis derivado.

**El texto no la contiene.** Al contrario, la contradice en los dos puntos donde debería aparecer:

- **6.1.13 Giroversión**: *"Se dibuja una flecha curva de **color azul**"* — sin condicional alguno.
- **5.13**: el color califica **al hallazgo**, no a la pieza.

Un diente con caries **y** giroversión lleva la caries en rojo y la flecha de giroversión en azul. **Cada marcador se pinta por su propio estado.**

Se elimina la regla. Es una simplificación: desaparece una dependencia entre hallazgos que obligaba a mirar la pieza entera para pintar uno solo.

### D3 — El odontograma se genera **por evento**, no por día (supersede `ADR-0012` D4)

`ADR-0012` D4 modeló **bloques diarios**, cerrados al terminar la atención. El texto fija otros disparadores:

> **5.10**: *"Se debe realizar el odontograma cuando **se inicia el plan de tratamiento**, se **evidencian nuevos hallazgos clínicos**, se **culmina el plan de tratamiento** y a **solicitud de procesos judiciales y personales**."*
> **5.11**: *"Una vez culminado el plan de tratamiento, en caso que el/la paciente **reingrese**... se genera un **nuevo odontograma**."*

**El aggregate sobrevive** —un odontograma fechado que se sella y no se reescribe (5.6)—; lo que cambia es **cuándo nace uno nuevo**. Deja de ser "un bloque por día" y pasa a ser "un odontograma por evento clínico que lo amerita".

Consecuencias: cae el invariante de **un bloque abierto por día**, y el cierre deja de atarse al fin de la jornada. **La cronología por expediente (`ADR-0012` D7) se mantiene**: sigue sin poder abrirse uno nuevo con otro sin cerrar.

> El **cierre diario sí aplica a la nota de evolución** (NTS 139, `update-clinical-history`). Confundir ambos documentos fue el origen del error.

### D4 — La localización tiene **cinco** formas, no tres

`ADR-0012` D8 definió superficie, pieza y entre-piezas. El texto exige dos más:

```
sealed interface Location
   ├── EnSuperficie(pieza, cara)      caries 36-O · restauración · sellante
   ├── EnPieza(pieza)                 corona · movilidad · ausente · implante
   ├── EntrePiezas(a, b)              diastema · supernumerario · fusión · transposición
   ├── EnRango(piezas[])              ⊕ prótesis parcial fija/removible · ortodoncia fija
   └── EnArco(maxilar)                ⊕ edéntulo total · prótesis completa · ortodoncia removible
```

Sin ellas, una prótesis completa superior habría que registrarla pieza por pieza —inventando 16 hallazgos donde la norma pide **uno**— o dejarla fuera del catálogo.

`EnRango` conserva el **orden y el conjunto** de piezas comprometidas (6.1.29 dibuja los pilares). `EnArco` toma `SUPERIOR` | `INFERIOR`.

### D5 — El tipo lleva **sigla oficial**, y muchos llevan **subtipo**

El texto agrupa hallazgos bajo un mismo concepto con siglas distintas según material, profundidad o causa:

| Concepto | Subtipos (siglas oficiales) |
|---|---|
| Lesión de caries (6.1.16) | `MB` mancha blanca · `CE` esmalte · `CD` dentina · `CDP` dentina/pulpa |
| Corona (6.1.3) | `CM` · `CF` · `CMC` · `CV` · `CLM` |
| Restauración definitiva (6.1.33) | `AM` · `R` · `IV` · `IM` · `IE` · `C` |
| Pieza ausente (6.1.20) | `DNE` no erupcionado · `DEX` por caries · `DAO` por otra razón |
| Posición anormal (6.1.28) | `M` · `D` · `V` · `P` · `L` |
| DDE (6.1.5) | `O` opacidades · `PE` pigmentación |
| Tratamiento de conducto (6.1.37) | `TC` · `PC` |
| Movilidad (6.1.19) | grado `1` · `2` · `3` |

Se modelan como **tipo + subtipo**, no como ~40 tipos planos: el comportamiento de color y el alcance pertenecen al **concepto** (toda corona es `SEGUN_ESTADO` y de alcance pieza), y el subtipo solo precisa cuál es. Aplanarlo repetiría esos atributos en cada variante, con el riesgo de que diverjan.

La **sigla es dato normativo** del subtipo (o del tipo, si no tiene): debe imprimirse exactamente. Se guarda como atributo, no como nombre del enum (`ADR-0013` D3).

### D6 — **Especificaciones** y **Observaciones** son dos campos con propósitos distintos

> **5.14 Especificaciones**: *"se registran los hallazgos o características clínicas adicionales... que **por la falta de espacio** no puedan ser registrados en los recuadros"*
> **5.15 Observaciones**: *"se describen los hallazgos clínicos que **no se encuentren contemplados en la nomenclatura** del registro del odontograma"*

`ADR-0012` modeló **uno solo**. Son dos, y la diferencia importa:

- **Especificaciones** detalla lo que **sí** está en el catálogo pero no cabe dibujado: el color del metal de una corona (6.1.3), el material de una corona temporal (6.1.4), la clasificación de fluorosis (6.1.5). ⚠️ **Corregido (2026-10-08):** esta línea incluía además *"la clasificación de movilidad"*, sin numeral que la respaldara. Al verificar el catálogo contra el texto oficial (tarea 0.7 de `add-dental-chart`, comentario de `V16`), 6.1.19 pone el grado de la movilidad **en la sigla** (`M1`/`M2`/`M3`), no en especificaciones. Solo esos tres tipos exigen especificación al sellar.
- **Observaciones** es la **vía normativa para lo que el catálogo no contempla**. Es el escape formal que la propia norma provee, y el lugar correcto para hallazgos sin sigla oficial.

El Anexo confirma dos áreas de texto separadas.

### D7 — El catálogo completo

38 hallazgos, tomados de 6.1.1–6.1.38. **Cierra la tarea `0.1`.**

> ✅ **Re-verificado entrada por entrada el 2026-09-23** contra el PDF oficial versionado (tarea `0.7`), tras una revisión externa que hizo dudar del catálogo. **Las 38 filas son correctas**: numeral, sigla, color y alcance. Las 6+22+4+3+3 entradas de las cinco secciones suman 38.
>
> ⚠️ **Lo que sí estaba mal era aguas abajo**, no acá: `regulatory-matrix.md` atribuía el implante a `6.1.16` (es **6.1.15**; 6.1.16 es la lesión de caries), y `proposal.md`/`tasks.md` daban la recesión gingival por `6.1.31` (es **prótesis dental parcial removible**). Corregidos.
>
> 🔴 **Dos typos de la propia norma**, transcritos tal cual con `[sic]`: la pieza en erupción aparece como **`5.2.23`** (debería ser 6.1.23) y el implante dental como **`6.115`** (debería ser 6.1.15). Ambos verificados en las páginas 14 y 11 del texto oficial. **No son errores de este ADR.**

| Alcance | Hallazgo | § | Sigla | Color |
|---|---|---|---|---|
| **superficie** | Lesión de caries | 6.1.16 | `MB` `CE` `CD` `CDP` | 🔴 |
| | Restauración definitiva | 6.1.33 | `AM` `R` `IV` `IM` `IE` `C` | ⚪ estado |
| | Restauración temporal | 6.1.34 | — | 🔴 |
| | Sellante | 6.1.35 | `S` | ⚪ estado |
| | Defectos del esmalte (DDE) | 6.1.5 | `O` `PE` | 🔴 |
| | Superficie desgastada | 6.1.36 | `DES` | 🔴 |
| **pieza** | Corona | 6.1.3 | `CM` `CF` `CMC` `CV` `CLM` | ⚪ estado |
| | Corona temporal | 6.1.4 | `CT` | 🔴 |
| | Espigo-muñón | 6.1.8 | — | ⚪ estado |
| | Fosas y fisuras profundas | 6.1.9 | `FFP` | 🔵 |
| | Fractura dental | 6.1.10 | — | 🔴 |
| | Geminación | 6.1.12 | — | 🔵 |
| | Giroversión | 6.1.13 | — | 🔵 |
| | Impactación | 6.1.14 | `I` | 🔵 |
| | Implante dental | **6.115** [sic] | `IMP` | ⚪ estado |
| | Macrodoncia | 6.1.17 | `MAC` | 🔵 |
| | Microdoncia | 6.1.18 | `MIC` | 🔵 |
| | **Movilidad patológica** | 6.1.19 | `M1` `M2` `M3` | 🔴 **(no azul)** |
| | Pieza ausente | 6.1.20 | `DNE` `DEX` `DAO` | 🔵 |
| | Pieza en clavija | 6.1.21 | — | 🔵 |
| | Pieza ectópica | 6.1.22 | `E` | 🔵 |
| | Pieza en erupción | **5.2.23** [sic] | — | 🔵 |
| | **Pieza extruida** | 6.1.24 | — | 🔵 **(no roja)** |
| | **Pieza intruida** | 6.1.25 | — | 🔵 **(no roja)** |
| | Pulpotomía | 6.1.27 | `PP` | ⚪ estado |
| | Posición anormal | 6.1.28 | `M` `D` `V` `P` `L` | 🔵 |
| | Remanente radicular | 6.1.32 | `RR` | 🔴 |
| | Tratamiento de conducto | 6.1.37 | `TC` `PC` | ⚪ estado |
| **entre piezas** | Diastema | 6.1.6 | — | 🔵 |
| | Fusión | 6.1.11 | — | 🔵 |
| | Pieza supernumeraria | 6.1.26 | `S` | 🔵 |
| | Transposición dentaria | 6.1.38 | — | 🔵 |
| **rango** | Aparato ortodóntico fijo | 6.1.1 | — | ⚪ estado |
| | Prótesis parcial fija | 6.1.29 | — | ⚪ estado |
| | Prótesis parcial removible | 6.1.31 | — | ⚪ estado |
| **arco** | Aparato ortodóntico removible | 6.1.2 | — | ⚪ estado |
| | Edéntulo total sup./inf. | 6.1.7 | — | 🔵 |
| | Prótesis completa sup./inf. | 6.1.30 | — | ⚪ estado |

🔴 **La sigla NO es única: dos colisiones reales en el catálogo oficial.**

| Sigla | Un tipo | Otro tipo | Cómo se distinguen |
|---|---|---|---|
| **`M`** | **Movilidad patológica** (6.1.19), 🔴 rojo | **Mesializado** (subtipo de Posición anormal, 6.1.28), 🔵 azul | la movilidad lleva **dígito de grado** (`M1`/`M2`/`M3`); mesializado va **sola** |
| **`S`** | **Pieza supernumeraria** (6.1.26), entre piezas | **Sellante** (6.1.35), superficie/pieza | por **alcance**: una va entre ápices, la otra en la pieza |

⚠️ **Consecuencia de modelado**: la sigla **no puede ser clave** del catálogo ni identificar un hallazgo por sí sola. La clave es el par **`tipo` + `subtipo`** (D5), y la sigla es un **atributo de salida**. Un índice único sobre la sigla rechazaría registros válidos.

**Dos casos con reglas propias:**

- **`DES` (6.1.36)** — *"se registra la sigla en el **recuadro correspondiente a la pieza** y se dibuja de color rojo en el **gráfico** acorde al lugar donde se observa"*. Se registra como hallazgo **de superficie**; la sigla del recuadro se **deriva** al renderizar. El modelo no cambia (era la opción recomendada en `ADR-0013` OQ2).
- **`IMP` (6.1.15)** — va en el recuadro de la pieza **reemplazada**, y *"sólo se registra cuando se observe clínicamente la presencia del implante"*. **No implica ausencia**: son hallazgos distintos (6.1.15 vs 6.1.20) que **coexisten** en la misma pieza. Cierra `ADR-0013` OQ3.

### D8 — Fuera del odontograma: IHO y registro periodontal

> 🔴 **Corregido en parte por [`ADR-0018`](0018-cumplimiento-nts-139-y-cop.md) D2.** La conclusión *"no está en NTS 188"* es correcta para los dos. Pero la frase de abajo *"si es obligatorio, será por NTS 139"* se escribió como hipótesis y **es un hecho**: NTS 139 **5.2.2 numeral 12.2** exige índice de higiene oral simplificado, índice de placa blanda y calcificada, y alta básica odontológica con fecha; el **12.1** exige odontograma **inicial y final**. Eso convierte el IHO en un gap 🔴 sobre `clinical-history`, **ya construida**, y descubre que el **odontograma final** no está en ningún artefacto — no se excluyó, se pasó por alto (va a `update-dental-chart`). **La exclusión de `add-dental-chart` se mantiene**: el IHO no pertenece al odontograma.

Revisado el texto completo, **ninguno de los dos aparece**:

- **Índice de Higiene Oral** — no se menciona. **No pertenece al odontograma.** Cierra la tarea `0.2`; si es obligatorio, será por NTS 139 y en la Ficha Odonto-Estomatológica, no acá. ✅ **Confirmado: lo es** (`ADR-0018` D2).
- **Recesión gingival** — 🔴 **tampoco está, y este ADR lo daba por incluido.** Verificado el 2026-09-23: el catálogo está en **orden alfabético** y entre `6.1.31` (Prótesis dental parcial removible) y `6.1.32` (Remanente radicular) **no hay hueco** — *"Rece"* ordena antes de *"Rema"*, así que si existiera estaría ahí. **No existe en NTS 188.** La exclusión de lo periodontal es entonces **más completa** de lo que decía este numeral: lo único periodontal del catálogo es la **movilidad** (6.1.19).
- **Sondaje periodontal y furca** — no se mencionan **ni en NTS 188 ni en el numeral 12 de NTS 139** (verificado, `ADR-0018` D2). La única condición periodontal del catálogo es la **movilidad** (6.1.19). Confirma que el **periodontograma es un documento aparte**. Cierra la tarea `0.3`.

## Consecuencias

- ✅ **La tarea bloqueante `0.1` se cierra**, y con ella `0.2` y `0.3`. `add-dental-chart` queda listo para implementar una vez actualizados sus artefactos.
- ✅ **El corte estado/acción queda confirmado textualmente**, que era la apuesta más fuerte de `ADR-0012`: *"En el odontograma únicamente se registran los hallazgos clínicos observados, **más no los procedimientos** a desarrollar según el plan de tratamiento"* (5.8).
- ✅ **Eliminar la regla de contagio simplifica el render**: cada marcador se pinta por su propio estado, sin mirar la pieza entera.
- ⚠️ **Dos alcances nuevos** (`EnRango`, `EnArco`) cambian el `Location` de `ADR-0012` D8 y el esquema de `add-dental-chart` design D2: el discriminador gana valores y el `CHECK` de coherencia se amplía.
- ⚠️ **`type` + `subtipo`** cambia el modelo de catálogo del design D6, y el `CHECK` pasa a validar el par.
- ⚠️ **Dos campos de texto** en vez de uno, y el checklist de sellado debe distinguirlos.
- ⚠️ **Cae el invariante de bloque diario** (`ADR-0012` INV-01 en su forma actual). La cronología por expediente se mantiene.
- ⚠️ **`add-dental-chart` necesita actualizarse**: proposal, design, spec y tasks se escribieron sobre el modelo anterior.

## Alternativas descartadas

- **Aplanar el catálogo en ~40 tipos sin subtipo** — descartada (D5): repetiría comportamiento de color y alcance en cada variante de material o profundidad, con riesgo de divergencia. El concepto es el que tiene esos atributos; el subtipo solo precisa cuál es.
- **Modelar "temporal" como un eje propio** — descartada (D1): los tipos temporales (`CT`, restauración temporal) son tipos distintos que simplemente declaran `SIEMPRE_ROJO`. Un eje aparte agregaría una dimensión sin ganar expresividad.
- **Registrar una prótesis completa como 16 hallazgos de pieza** — descartada (D4): inventaría datos donde la norma pide **un** hallazgo, y haría imposible distinguir una prótesis completa de 16 coronas.
- **Conservar la regla de contagio "por si acaso"** — descartada (D2): no está en el texto, complica el render y crea una dependencia entre hallazgos que la norma no pide. Si apareciera en una versión futura, se agrega entonces.
- **Mantener el bloque diario y tratar los eventos de 5.10 como casos particulares** — descartada (D3): invertiría la regla. La norma dice cuándo se genera un odontograma, y "todos los días" no está en esa lista.

## Lección registrada

**Un análisis derivado de una norma no es la norma.** Los documentos de `docs/compliance/` traducen bien el espíritu, pero en el detalle —qué color lleva la movilidad, si existe el pintado condicional, cuándo se genera un odontograma nuevo— se apartaron del texto en tres puntos, y uno de ellos se dio por **confirmado** en `ADR-0013` sin serlo.

Lo que funcionó, y conviene mantener:

1. **Ambos ADR marcaron sus afirmaciones normativas como pendientes de validación.** Eso hizo el error visible en vez de enterrarlo.
2. **La validación del catálogo era tarea bloqueante *previa a la migración*.** Sin esa barrera, estos tres errores habrían llegado a un `CHECK` de Postgres y a datos reales.
3. **Cerrar una Open Question exige el texto, no un resumen.** El error concreto fue marcar *"movilidad en azul — CONFIRMADA"* citando un análisis derivado.

Regla para `docs/adr/README.md` (dimensión 1): una fila de la matriz solo pasa a ✅ cuando se cita **artículo del texto oficial**, no un documento que lo interpreta.
