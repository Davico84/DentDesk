# Matriz de trazabilidad normativa

Mapea **cada requisito normativo** que el sistema debe cumplir con **la decisión que lo resuelve** y **el artefacto que lo implementa**. Sirve para responder, ante una auditoría de SUSALUD o del MINSA, *"¿cómo cumple el sistema este requisito?"* siguiendo un rastro verificable en vez de un grep.

## Qué NO es este documento

**No es un resumen de las normas.** Acá solo se **enlaza** requisito ↔ decisión ↔ artefacto.

> 🔴 **Los documentos hermanos de esta carpeta NO son fuente normativa citable** ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) D8). `nts-139-history-clinical.md`, `nts-188-odontogram.md`, `nts-150-to-188-transition.md`, `cop-code-of-ethics.md` y `legal-retention.md` son **análisis derivados**, escritos sin el texto oficial delante. Esta línea antes los declaraba *"el análisis por norma"*, y de ahí salieron **todos** los § inventados que `ADR-0017` y `ADR-0018` tuvieron que corregir. Sirven como inventario de hipótesis y de impacto técnico ya razonado; **no** para cerrar una fila. El § de una fila sale del texto oficial, o la fila no se cierra.

**No es un segundo hogar para las decisiones.** El *por qué* vive en los ADR; el *qué construir*, en los changes de OpenSpec.

## La cadena de trazabilidad

```
 norma  ──▶  ADR  ──▶  requirement / scenario  ──▶  test  ──▶  commit
   ▲          ▲              ▲                       ▲          ▲
   │          │              │                       │          │
   └── esta   └── docs/adr/  └── openspec/specs/      │    commits.md §4b
       matriz                                        │    "Refs: <change> #<task>"
                                                testing.md §0
                                        "el nombre del test DEBE
                                         referenciar el scenario"
```

> ⚠️ **El eslabón del test solo se puede nombrar cuando el requisito está construido.** Mientras un change esté propuesto y no archivado, la columna llega hasta el **scenario** — no hay test al que apuntar todavía. Para las filas ✅ se nombra la clase de test concreta.

## Cómo mantenerla

- **Al escribir un ADR** con motivación normativa: agregar o actualizar su fila (`docs/adr/README.md`, dimensión 1).
- **Al archivar un change**: reemplazar `pendiente` por el requirement/scenario real y actualizar el estado.
- **Nunca poner "Sí"** en la columna de artefacto. Un "Sí" es una luz verde que nadie verifica — el mismo modo de falla que dejó reglas ArchUnit en verde durante meses sin comprobar nada (`docs/testing.md §5`). Apuntar siempre al **nombre concreto** del requirement o del scenario.

## Normas de referencia

| Norma | Instrumento | Ámbito |
|---|---|---|
| **NTS N°139-MINSA/2018/DGAIN** | RM N°214-2018/MINSA **+ modificatoria RM N°265-2018/MINSA** | Gestión de la Historia Clínica — ✅ **texto oficial leído** (2026-09-23, `ADR-0018`) |
| **NTS N°188-MINSA/DGIESP-2022** | RM N°559-2022/MINSA (El Peruano, 29/07/2022) | Uso del Odontograma — **deroga** NTS N°150 (art. 2 deroga la RM N°595-2019). ✅ **texto oficial leído** (2026-08-03, `ADR-0017`) |
| **Ley N°29733** | — | Protección de Datos Personales — ✅ **texto oficial leído y versionado** (2026-09-23, [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md)). ⚠️ original 2011, sin modificatorias |
| **D.S. N°016-2024-JUS** | El Peruano 30.11.2024, vigor 30.03.2025 | Reglamento **vigente** de la Ley 29733 — ✅ **texto oficial leído y versionado** (2026-09-23, [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md)). 🔴 **Deroga el D.S. N°003-2013-JUS**, que este proyecto llegó a leer entero antes de comprobar su vigencia |
| **Ley N°27269** + D.S. N°052-2008-PCM | — | Firmas y Certificados Digitales — ⚠️ **texto NO verificado**. Tras `ADR-0018` D4 es la **única** fuente de la firma digital: el COP no la menciona en ningún artículo |
| **Código de Ética y Deontología del COP** | Resolución N°01-02.SOCN.COP.2024 (16.02.2024), 104 artículos · marco: Ley N°27878, D.S. N°016-2005-SA | Ejercicio profesional del Cirujano Dentista — ✅ **texto oficial leído** (2026-09-23, `ADR-0018`) |

**Estado:** ✅ construido y testeado · 🚧 decidido, no construido · ⚠️ sin decisión · 🔴 **gap sobre capacidad ya construida**

---

## Historia clínica

> 🔴 **Los § de NTS 139 de esta sección se reescribieron contra el texto oficial** ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) D3, 2026-09-23). Los anteriores —`6.1.1 a`, `6.1.4`, `5.4.1 f`, `5.4.1 i`— **no existen en la norma**: venían del análisis derivado. ⚠️ En los siete casos **el requisito era correcto y solo la cita era falsa**, que es lo que hizo el error invisible a dos revisiones.

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| Registro secuencial y cronológico de la atención | NTS 139 | **5.3.1 (7)** · **5.3.3 h** | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | `clinical-history` → *Información inicial sellada tras COMPLETED* · `ClinicalHistoryServiceIT` | ✅ |
| Prohibición de enmiendas, raspaduras o destrucción de folios | NTS 139 | **5.3.1 (8) b** · **5.3.3 j** | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Rechazar la edición de la información sellada aun por API directa* · `ClinicalHistoryControllerIT`. Y bajo concurrencia (`update-clinical-history`): scenarios *Guardar la Historia mientras otra operación la sella no la reabre* y *Los diagnósticos de una Historia que se está sellando no cambian* · `ClinicalHistoryConcurrencyIT` | ✅ |
| El diagnóstico lleva tipo obligatorio: presuntivo / definitivo / repetido | MINSA HIS | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Rechazar un diagnóstico sin tipo (P/D/R)* · `ClinicalHistoryDiagnosisServiceIT` | ✅ |
| Cada diagnóstico registra fecha/hora y profesional, de forma inmutable | MINSA HIS | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Agregar un diagnóstico general* · `ClinicalHistoryDiagnosisServiceIT` | ✅ |
| El diagnóstico usa el catálogo CIE10 oficial, respetando su jerarquía | MINSA (CIE10) | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Buscar un código navegando la jerarquía* · `Cie10SearchServiceIT` | ✅ |
| El menor requiere representante legal que autorice procedimientos | COP / Código Civil | *pend.* | [`ADR-0007`](../adr/0007-tutor-menores.md) | `patients` → *responsable obligatorio para menores* | ✅ datos · 🚧 consentimiento (`treatments`) |
| **Filiación completa y obligatoria, incluido Ubigeo de nacimiento y procedencia** | NTS 139 | **5.3.1 (1) b** · **5.3.3 d** | [`ADR-0015`](../adr/0015-identidad-del-paciente.md) — exigida **al abrir la HC**, no al admitir ni al sellar. ⚠️ El *momento* **no está verificado**: la frase que lo fijaba era una inferencia del análisis derivado citada como norma ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) D3). La decisión se mantiene por su argumento de diseño | pendiente — `update-patients` + `update-clinical-history` | 🚧 |
| **Identificador único: DNI/CE con formato validado** | NTS 139 | **5.3.3 e** | [`ADR-0009`](../adr/0009-documento-unico-por-clinica.md) + [`ADR-0015`](../adr/0015-identidad-del-paciente.md) | `patients` → documento único por clínica y emparejado con su tipo · `ValidDocumentValidatorTest` | ✅ formato · 🚧 obligatoriedad |
| Evolución estructurada en SOAP (Subjetivo/Objetivo/Análisis/Plan) | NTS 139 | **5.2.2** · **4.2.9** | ⚠️ sin ADR | `update-clinical-history` (fase 2) | ⚠️ |
| Enmienda como asiento nuevo vinculado, sin alterar el original | NTS 139 | **5.3.1 (8) b** | ⚠️ sin ADR — diferido en `ADR-0011` | `update-clinical-history` (fase 2) | ⚠️ |

### Ficha Odonto-Estomatológica — NTS 139 § 5.2.2 numeral 12

> 🔴 **Gap sobre capacidad CONSTRUIDA.** `clinical-history` está archivada y en uso desde el 2026-07-30, y la ficha que la norma exige está incompleta. No es *"todavía no lo hacemos"*: es una ficha normativa que el sistema ya pretende cubrir. Descubierto al leer el texto oficial ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) D2), que corrige la inferencia de `ADR-0017` D8 — el IHO **no** va en el odontograma, pero **sí** es obligatorio.

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| **Odontograma inicial y final** | NTS 139 | **5.2.2 (12.1)** | `ADR-0012` (inicial) · ⚠️ **el final sin ADR** | inicial: ✅ `dental-chart` (archivado 2026-10-08) · final: **ningún artefacto** — no se excluyó, se pasó por alto; va a `update-dental-chart` (fase 2) | 🔴 |
| **Índice de higiene oral simplificado (IHO-S)** | NTS 139 | **5.2.2 (12.2)** | ⚠️ sin ADR — [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D2** abre el gap, no lo cierra | pendiente — `update-clinical-history`. Tiene índice **por pieza indicadora** + valor agregado: es modelo de datos, no una columna | 🔴 |
| **Índice de Placa Blanda y Placa Calcificada** | NTS 139 | **5.2.2 (12.2)** | ⚠️ sin ADR | pendiente — `update-clinical-history` | 🔴 |
| **Alta básica odontológica con fecha** | NTS 139 | **5.2.2 (12.2)** | ⚠️ sin ADR | pendiente — `update-clinical-history`. ⚠️ Es un **estado del episodio**, no un campo de texto: hay que decidir qué lo habilita | 🔴 |

### Exigencias de NTS 139 sin artefacto

> Descubiertas al leer el texto oficial ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) D7). No son correcciones: son requisitos que **ningún** ADR, spec ni change mencionaba. Se listan para que tengan dueño.

| # | Requisito normativo | Norma | § | Estado | Nota |
|---|---|---|---|---|---|
| N1 | **La identificación del paciente se registra separada de la atención** | NTS 139 | **5.3.3 e** | ⚠️ | `patients` y `clinical-history` ya están separados, así que probablemente **se cumple por accidente**. Verificar contra el § antes de ponerlo ✅ — cumplir sin saberlo no es trazabilidad. |
| N2 | **El número de historia clínica es el DNI del paciente** | NTS 139 | **5.3.3 e** | ⚠️ **choca con decisión tomada** | [`ADR-0009`](../adr/0009-documento-unico-por-clinica.md) hace el documento único **por clínica**, y el modelo multi-tenant admite el mismo DNI en dos clínicas — correcto como negocio. Si el número de HC **es** el DNI, el identificador del expediente no es global, y el paciente sin DNI (CE, recién nacido) necesita regla propia. **Necesita ADR.** |
| N3 | **Acreditación ante el RENHICE** | NTS 139 | **5.3.3 l** | ⚠️ | Registro Nacional de Historias Clínicas Electrónicas. Condiciona el valor legal pleno de la HC electrónica. **Trámite institucional del titular, no código** — dueño: el usuario, no el repo. Si nunca se hace, la premisa del producto se debilita. |
| N4 | **Consentimiento explícito para el tratamiento de datos personales** (Anexo 11) | NTS 139 | **5.3.3** + Anexo 11 | ⚠️ | **Distinto** del consentimiento *clínico* de [`ADR-0007`](../adr/0007-tutor-menores.md) y `ADR-0016` D6: otro texto, otro momento, otro fundamento legal. El proyecto modela uno solo. Cruza con Ley 29733, sin verificar. |
| N5 | **Copia de la historia clínica al paciente en 5 días** | NTS 139 | **5.3.3 r** | ⚠️ | Plazo duro. Le pone urgencia y plazo a `ADR-0014` **D3** (exportación formal del expediente), que hasta ahora no tenía ninguno de los dos. |

## Odontograma

[`ADR-0012`](../adr/0012-odontograma-libro-de-asientos.md) + [`ADR-0013`](../adr/0013-odontograma-nts-188.md) + [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md). `add-dental-chart` está **construido y archivado** (2026-10-08): los requirements viven en `openspec/specs/dental-chart/spec.md` y el sellado coordinado en `openspec/specs/clinical-history/spec.md`. Las filas ✅ nombran la clase de test; las 🚧/🔴 que quedan son de la **fase 2** (evolución y odontograma final).

> 🔴 Las filas de abajo se reescribieron contra el **texto oficial** de NTS 188 (`ADR-0017`). Las anteriores citaban un *análisis derivado* de la norma y tenían el § mal (todo colgaba de un supuesto "Anexo N°01"; la nomenclatura vive en **6.1.1–6.1.38**) y tres colores mal. **Una fila no pasa a ✅ sin citar el artículo del texto oficial.**
>
> 🔴 Las filas que citan **NTS 139** (no NTS 188) se corrigieron aparte, con [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D3** — arrastraban los mismos § inventados que la sección anterior.

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| **Un odontograma nuevo por evento** (inicio y fin del plan, hallazgo nuevo, reingreso, pedido judicial), no por día | NTS 188 | **5.10 / 5.11** | [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D3** ⚠️ corrige el *bloque diario* de `ADR-0012` D4 | inicial: `dental-chart` → *Odontograma inicial único por paciente y clínica* (`DentalChartServiceIT`) · los odontogramas **por evento** siguientes: pendiente — `update-dental-chart` (fase 2) | 🚧 |
| Estructura de dentición permanente y decidua en notación **FDI** | NTS 188 | **5.4** | `add-dental-chart` design **D7** | `dental-chart` → *Piezas y superficies válidas según FDI* · scenarios *Rechazar una pieza inexistente*, *Rechazar una superficie no aplicable a la pieza* (`ValidFindingLocationValidatorTest`, `dentalChart.test.ts`, `Odontogram.test.tsx`) | ✅ |
| **Código binario**: azul = hallazgo en buen estado · rojo = mal estado, **temporal** o patológico | NTS 188 | **5.12 / 5.13** | [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D1** (tres comportamientos: `SIEMPRE_AZUL`, `SIEMPRE_ROJO`, `SEGUN_ESTADO`) · `ADR-0013` **D5** (hex normativos) | `dental-chart` → *Color del hallazgo según el comportamiento de su tipo* · scenarios *Registrar una restauración en buen estado* / *…en mal estado* (`DentalChartFindingServiceIT`, `Odontogram.test.tsx`, `FindingsSummary.test.tsx`) | ✅ |
| Sigla oficial y subtipo por hallazgo — **catálogo completo de 38** | NTS 188 | **6.1.1 – 6.1.38** | [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D5** + **D7** | `dental-chart` → *Catálogo cerrado de condiciones, alineado a la norma vigente* · scenarios *Registrar un hallazgo con su subtipo*, *Rechazar un tipo de condición fuera del catálogo* (`FindingTypeCatalogTest`, `DentalChartControllerIT`) | ✅ |
| Movilidad en arábigos `M1`/`M2`/`M3`, **en ROJO** | NTS 188 | **6.1.19** | [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D7** ⚠️ corrige a `ADR-0013`, que la daba por azul | `dental-chart` → *Grado y especificación obligatorios según el tipo* · scenarios *Registrar una condición que exige grado*, *Rechazar una condición que exige grado sin indicarlo* (`ValidFindingLocationValidatorTest`, `Odontogram.test.tsx`) | ✅ |
| Giroversión: flecha curva **azul**, sin condicional | NTS 188 | **6.1.13** | [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D2** ⚠️ la *regla de contagio* de `ADR-0012` **no existe en la norma** | `dental-chart` → *Color del hallazgo según el comportamiento de su tipo* · scenario *Un hallazgo sin estado convive con una patología sin cambiar por ella* (`DentalChartFindingServiceIT`, `Odontogram.test.tsx`) | ✅ |
| Sellante **coexiste** con caries adyacente en la misma superficie | NTS 188 | **6.1.35** | [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D7** | `dental-chart` → scenario *Una restauración y una patología coexisten en la misma pieza* (`DentalChartFindingServiceIT`) | ✅ |
| El odontograma es anexo de la historia clínica y se sella con la admisión | NTS 139 | **5.2.2 (12.1)** | `ADR-0012` **D5** · fundamento actualizado por `ADR-0013` **D1** | `clinical-history` → *Sellado a COMPLETED con checklist mínimo afirmativo* (MODIFICADO por `add-dental-chart`) · scenarios *Rechazar el sellado sin odontograma inicial*, *Un fallo al sellar el odontograma deja la Historia en DRAFT* (`CloseAdmissionServiceIT`). ⚠️ La **filiación** se exige aparte, en la *apertura* (`ADR-0015`) — requirement distinto | ✅ |
| El odontograma inicial es inalterable | NTS 139 | **5.3.1 (8) b** · **5.3.3 j** | `ADR-0012` **D4** · `add-dental-chart` design **D9** (bloqueo en el servicio + triggers `V20`) | `dental-chart` → *Odontograma sellado e inmutable* y *El sellado del odontograma es irreversible y protege sus hallazgos* (`DentalChartSealGuardIT`, `CloseAdmissionServiceIT`, `DentalChartControllerIT`) | ✅ |
| **Especificaciones** (lo que no cabe en el recuadro) y **Observaciones** (lo que no está en la nomenclatura): **dos campos distintos** | NTS 188 | **5.14 / 5.15** | [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D6** | `dental-chart` → *Especificaciones y observaciones del odontograma* · scenario *Distinguir una sección revisada y vacía de una nunca revisada* (`DentalChartServiceIT`, `DentalChartControllerIT`) | ✅ |
| Especificación **obligatoria** para corona (color del metal), corona temporal (material) y fluorosis (clasificación) — la movilidad **no**: su grado va en la sigla | NTS 188 | **6.1.3 · 6.1.4 · 6.1.5** · **6.1.19** | [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D6** ⚠️ corregido el 2026-10-08: listaba la movilidad sin numeral | `dental-chart` → *Grado y especificación obligatorios según el tipo* · scenario *Rechazar el sellado si un tipo que exige especificación no la tiene* (`DentalChartChecklistTest`, `CloseAdmissionServiceIT`, `dentalChart.test.ts`) | ✅ |
| Localización del hallazgo: superficie, pieza, entre piezas, **rango** y **arco** | NTS 188 | **6.1.1 – 6.1.38** | [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D4** — cinco formas | `dental-chart` → *Registro de hallazgos con localización precisa* · scenarios *Registrar un hallazgo entre dos piezas*, *…sobre un rango de piezas*, *…sobre un arco completo* (`DentalChartFindingServiceIT`, `Odontogram.test.tsx`, `FindingDialog.test.tsx`) | ✅ |
| El odontograma registra **hallazgos observados**, no los procedimientos del plan | NTS 188 | **5.8** | `docs/vision.md` §2 (estado vs. acción) + [`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md) **D3** | `dental-chart` → *Purpose* (delimita el alcance; no tiene test propio: lo garantiza que el catálogo solo contiene hallazgos) | ✅ |
| Cierre transaccional atómico de evolución **y** odontograma del día | NTS 139 | **5.3.1 (7)** | `ADR-0012` **D5** | pendiente — `update-dental-chart` (fase 2) | 🚧 |
| Bloqueo del expediente con atención anterior sin cerrar | NTS 139 | **5.3.1 (7)** ⚠️ inferido, no textual | `ADR-0012` **D7** | pendiente — `update-dental-chart` (fase 2) | 🚧 |
| Odontograma de evolución comparable entre fechas | NTS 188 | **5.10** | `ADR-0012` **D3** | pendiente — `update-dental-chart` (fase 2) | 🚧 |
| **Odontograma FINAL** (el inicial no basta) | NTS 139 | **5.2.2 (12.1)** | ⚠️ sin ADR — descubierto por [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D2** | **ningún artefacto**: `add-dental-chart` cubre el inicial y **no lo excluye en sus Non-goals** — se pasó por alto. Va a `update-dental-chart` | 🔴 |

## Retención y ciclo de vida

> 🔴 **El plazo escrito era más corto que el legal: 15 años en vez de 20** ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) D1). Es el error más caro de toda la auditoría, porque los demás son citas equivocadas y este era una **política de retención**: implementado como precondición de purga, habría autorizado destruir expedientes con protección legal vigente durante cinco años. Corregido **antes de existir como código**. Dos fuentes oficiales independientes coinciden en 20: NTS 139 **5.3.1 (6) e** y **COP art. 33** (*"un mínimo de veinte (20) años"*).

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| Conservación íntegra **20 años** desde la última atención | NTS 139 · COP | **5.3.1 (6) e** · **COP art. 33** | [`ADR-0014`](../adr/0014-ciclo-de-vida-del-documento-clinico.md) **D5**, corregido por [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D1** — desde el último registro clínico, sellado o no | pendiente | 🚧 |
| **40 años** si hay diagnóstico de cáncer de origen ocupacional | NTS 139 | **5.3.1 (6) e** | [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D1** — ⚠️ caso **nuevo**, no estaba en ningún artefacto | pendiente. ⚠️ Requiere saber si el expediente tiene ese diagnóstico: es una consulta al CIE10 registrado, no un campo aparte | ⚠️ |
| Archivo **activo** (5 años) y **pasivo** (15 años más) | NTS 139 | **5.3.1 (4) k** · **5.3.1 (4) l** | [`ADR-0014`](../adr/0014-ciclo-de-vida-del-documento-clinico.md) **D6** — estado **derivado**, no almacenado | pendiente | 🚧 |
| Destrucción solo tras **20 años** y con acta del Comité | NTS 139 | **5.3.1 (6) e** · **5.3.2 (1) c** | [`ADR-0014`](../adr/0014-ciclo-de-vida-del-documento-clinico.md) **D7** — **no se construye**: cumple por ausencia | sin endpoint de borrado · `DELETE` revocado al rol de app (D4) | 🚧 |
| Respaldos **seguros y continuos**, con **verificación de integridad** | D.S. 016-2024-JUS | **48.1 num. 3** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D9** — cierra el hueco: ya no es "sin verificar". Referencia sugerida: NTP-ISO/IEC 27001:2022 | pendiente — ⚠️ **sin dueño técnico**. Exige **verificar** la copia, no solo hacerla | 🔴 |

## Seguridad, secreto profesional y no repudio

> 🔴 **Los cuatro artículos del COP que citaba esta sección apuntaban a otra materia** ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) D4). El secreto profesional está en los **arts. 74–77**, no 31–36; el **art. 42** es *interconsulta*; los **52–53** son *competencia desleal* y *atención al colega*; el **54** es *prohibición de comisiones*. **El COP no menciona la firma digital en ningún artículo.**
>
> ⚠️ **Lo que no se pudo verificar queda marcado, no reemplazado.** Asignar un artículo plausible a una fila sin releer el texto sería repetir exactamente el error que se está corrigiendo. Un `⚠️ sin verificar` es información; una cita verosímil e inventada es peor que ninguna, porque nadie la vuelve a revisar.

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| Aislamiento entre clínicas; la historia es privada del paciente | Ley 29733 / NTS 139 | — | [`ADR-0004`](../adr/0004-el-paciente-pertenece-a-una-clinica.md) | `multi-tenancy` + test de aislamiento **obligatorio** · `ClinicalHistoryRlsIT` · `PatientRlsIT` · `MembershipRlsIT` | ✅ |
| **Secreto profesional**: recepción no accede a SOAP, hallazgos ni CIE10 | COP | **Arts. 74–77** | [`ADR-0016`](../adr/0016-responsabilidad-clinica.md) **D2/D3** — se clasifica el dato, y se **deniega** en vez de filtrar | pendiente — **bloqueado por `staff`** | 🚧 |
| **Autoría por profesional habilitado**, sin cuentas compartidas | NTS 139 · COP | **5.3.1 (7)** · **5.3.3 h** · **COP art. 65** (habilitación) | [`ADR-0016`](../adr/0016-responsabilidad-clinica.md) **D1** — la credencial se **copia** al firmar, no se referencia · refinado por [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D6**: la credencial es la **habilitación** (estado mutable), no la colegiatura (número permanente). Se guardan **ambos** datos | pendiente — **bloqueado por `staff`**. ⚠️ El COP **no tiene** un artículo de "autoría"; el ~~art. 42~~ que se citaba es *interconsulta* | 🚧 |
| **Firma digital** del cierre con certificado acreditado | Ley 27269 | ⚠️ **sin verificar** — texto no leído | [`ADR-0014`](../adr/0014-ciclo-de-vida-del-documento-clinico.md) **D2** — diferida, con el hash (D1) como punto de extensión. ✅ La decisión de **posponer se refuerza**: [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D4** le quitó una de sus dos patas — el COP **no** exige firma digital, los ~~arts. 52–53~~ son competencia desleal y atención al colega | pendiente | ⚠️ |
| Integridad: prohibido alterar, falsear o destruir registros | NTS 139 · COP | **5.3.1 (8) b** · **COP art. 33** | `ADR-0011` + `ADR-0012` **D3** | sellado server-side, libro de asientos. ⚠️ El ~~art. 54~~ que se citaba es *prohibición de comisiones* ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) D4) | ✅ parcial |
| 🔴 **No cooperar en la creación o comercialización de bancos de datos de salud electrónicos** | COP | **Art. 78** | [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D5** — el dato clínico **no cruza el límite del tenant** para ningún propósito, incluido el análisis agregado | [`ADR-0004`](../adr/0004-el-paciente-pertenece-a-una-clinica.md) + RLS · `ClinicalHistoryRlsIT`. ⚠️ **Sin decisión escrita**: que ninguna funcionalidad monetice el dato clínico (benchmarking entre clínicas, venta de estadísticas, entrenamiento de modelos) hoy se cumple solo porque la funcionalidad no existe | ⚠️ |
| **Cifrado en reposo** de datos clínicos | — | ⚠️ **ninguno: la norma NO lo manda** | [`ADR-0016`](../adr/0016-responsabilidad-clinica.md) **D5** — columnas de clase clínica, una llave por entorno. [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D9**: verificado contra los **dos** textos, `AES-256` y `TLS 1.3` **no salen de la norma**. Sobrevive como **decisión de ingeniería**, sin el respaldo normativo que esta fila le atribuía | pendiente | 🚧 |
| **Cifrado en TRÁNSITO y en la exportación** (+ firmas digitales, checksum) | D.S. 016-2024-JUS | **52** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D9** — esto **sí** está mandado: aplica a `ADR-0014` **D3** y a la devolución de [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D2** | pendiente | 🔴 |
| **Registro de interacciones**: cuentas, horas de inicio/cierre de sesión y acciones de **visualización**, modificación, eliminación, importación y exportación | D.S. 016-2024-JUS | **46 num. 3** | [`ADR-0016`](../adr/0016-responsabilidad-clinica.md) **D4**, **cerrado** por [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D5**. ✅ Acertó: *"visualización"* está en el texto. ⚠️ Hay que **agregar** horas de sesión, generación continua, disponibilidad inmediata y **retención mínima de 2 años**; y **quitar** el *"no borrable"* — el art. 46.3 exige **procedimiento de disposición y destrucción** | pendiente | 🔴 |
| **Control de acceso**: ciclo de vida del usuario, autenticación y **verificación de privilegios mínimo semestral** | D.S. 016-2024-JUS | **46 num. 1** | ⚠️ sin ADR — [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D5** lo nombra. Es obligación **operativa con reloj**, no una función | pendiente — ninguna capacidad lo cubre | 🔴 |
| **Capacitación del personal** según roles, y control de copias / canales no institucionales | D.S. 016-2024-JUS | **46 num. 2 y 4** | ⚠️ sin ADR — es **política, no código** ([`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D5**). ⚠️ El num. 4 nombra el riesgo real de una clínica: mandar una radiografía por mensajería no institucional | pendiente | ⚠️ |

---

## Protección de datos personales

> **El marco que faltaba** ([`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) D1): la **clínica** es el *titular del banco de datos*, **Dentdesk** es el *encargado del tratamiento* y el **paciente** es el *titular de los datos*. ⚠️ La ley usa *"titular"* para dos cosas distintas; acá se escriben completas.
>
> 🔴 **Tres de estas filas están tipificadas como infracción grave hoy** (D.S. 016-2024-JUS art. 133). No son riesgos teóricos: son conductas descritas en el catálogo de infracciones.

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| **Documento de Seguridad** aprobado, con **fecha cierta**, procedimientos de acceso e **inventario de datos** marcando los sensibles | D.S. 016-2024-JUS | **47** | ⚠️ **sin ADR** — [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D6** abre el gap. Quién lo firma (Dentdesk, cada clínica o ambos) **necesita ADR propio** | **no existe**. Documento de gobernanza, no código. ✅ El inventario es parcialmente derivable del esquema (`ADR-0016` D5) | 🔴 **art. 133 num. 5: grave** |
| **Notificación de incidentes**: Dentdesk avisa a la clínica **de inmediato**; la clínica a la autoridad y al paciente en **48 h** | D.S. 016-2024-JUS | **34** · **35** · **36** | ⚠️ sin ADR — [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D7** | **no existe**. ⚠️ La obligación *"permanece aun cuando el incidente haya sido subsanado internamente"* | 🔴 **art. 133 num. 12: grave** |
| **Oficial de Datos Personales** designado, publicado y comunicado a la autoridad en **15 días** | D.S. 016-2024-JUS | **37** · **38** · **39** | ⚠️ sin ADR — [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D8**. Obligatorio por **giro de negocio con datos sensibles** (37.1 num. 3) | **no designado**. ✅ Admite función compartida e incluso persona externa (38.2). ⚠️ **Traba la fila anterior**: la notificación debe incluir sus datos (34.2 num. 2) | 🔴 |
| El dato clínico **no se usa para otra finalidad** que la del contrato | Ley 29733 · D.S. 016-2024-JUS | **art. 30** · **31.1** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D1** — convierte en obligación legal la regla ética de `ADR-0018` D5 | [`ADR-0004`](../adr/0004-el-paciente-pertenece-a-una-clinica.md) + RLS · `ClinicalHistoryRlsIT`. **Decidido**: ni benchmarking, ni venta de estadísticas, ni entrenamiento de modelos | ✅ parcial · art. 133 num. 7 |
| **Baja del tenant**: devolver el expediente a la clínica y **después** suprimir, máximo **2 años** | D.S. 016-2024-JUS | **31.2** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D2** — cierra la **Open Question 3** de `ADR-0014` D7 | pendiente. 🔴 Asciende `ADR-0014` **D3** (exportación): *"la conservación indeterminada está prohibida"* | 🔴 |
| **Consentimiento de datos sensibles por escrito**, con firma manuscrita, digital o **electrónica** | D.S. 016-2024-JUS | **8** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D4** | pendiente. ✅ **No exige certificado IOFE**: se desacopla de la firma digital pospuesta en `ADR-0014` D2 | 🚧 |
| **Carga de la prueba del consentimiento** recae en el titular del banco (la clínica) | D.S. 016-2024-JUS | **9** | [`ADR-0016`](../adr/0016-responsabilidad-clinica.md) **D6** — [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D4** le da su mejor argumento: sin registrar **qué versión** se firmó, no se prueba el *"informado"* | pendiente — `treatments` | 🚧 |
| **Revocación** del consentimiento en cualquier momento, con la misma formalidad | Ley 29733 · D.S. 016-2024-JUS | **13.7** · **10** | `ADR-0016` **D6** | pendiente. ⚠️ Revocar también es **por escrito**: no es un botón sin artefacto | 🚧 |
| Menores y adolescentes ejercen por **representante legal** | Ley 29733 | **13.3** | [`ADR-0007`](../adr/0007-tutor-menores.md) — gana respaldo directo ([`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D4**) | `patients` → *responsable obligatorio para menores* | ✅ datos · 🚧 consentimiento |
| **Estado de bloqueo** mientras se tramita rectificación o supresión | Ley 29733 | **art. 20, párr. 3** | ⚠️ sin ADR — [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D10** | **no existe**. ⚠️ Choca con el gate `enabledForTreatment`: bloqueado no es incompleto | 🔴 |
| **Plazos de respuesta** al paciente: información 8 d · acceso 20 d · rectificación/cancelación/oposición 10 d | D.S. 016-2024-JUS | **69** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D10** | pendiente. ⚠️ **NTS 139 5.3.3 r manda el más corto: 5 días** para la copia de la historia clínica | 🔴 art. 133 num. 1 |
| **Inscripción del banco de datos** en el Registro Nacional | D.S. 016-2024-JUS | **42** · **43** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D11** — obligación **de la clínica**, no de Dentdesk | ✅ Dentdesk provee la **descripción técnica de las medidas de seguridad**, que solo nosotros tenemos. La inscripción **es gratuita** (43.3) | ⚠️ dueño: el cliente |
| **Confidencialidad** que subsiste tras terminar la relación | Ley 29733 | **art. 17** | `ADR-0016` **D2/D3** | pendiente — **bloqueado por `staff`** | 🔴 art. 133 num. 11 |
| La exención de consentimiento para datos de salud **exige observar el secreto profesional** | Ley 29733 | **14 num. 6** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D3** | 🔴 **Hoy cualquier miembro del tenant ve toda historia clínica.** Sin secreto profesional cae la exención, y se tratan datos sensibles sin consentimiento válido | 🔴 **art. 133 num. 3 y 5: grave** |
| Aplicación de la ley peruana **aunque el encargado esté fuera del Perú** | D.S. 016-2024-JUS | **art. VI num. 2** *(Título Preliminar, romanos)* | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D1** — *"Sea efectuado por un encargado del tratamiento, **con independencia de su ubicación**, a nombre de un titular (…) establecido en territorio peruano"*. El hospedaje **no exime** | ⚠️ Ningún artefacto dice dónde se hospeda Dentdesk | ⚠️ |

## Huecos abiertos

Agrupados por **qué documento los resuelve**, no por tema. Un gap sin dueño no se cierra: saber si le toca un ADR, un spec o una política es la mitad del trabajo.

Los 🔴 afectan **capacidades ya construidas y en uso**. Los ⚠️ son decisiones pendientes sin incumplimiento activo.

> **Plan acordado (2026-08-03):** en vez de abrir seis changes paralelos, **tres ADR transversales** cubren la mayoría de estos huecos manteniendo coherencia arquitectónica. Ninguno está escrito todavía.

### A. Decisiones de arquitectura → ✅ [`ADR-0014`](../adr/0014-ciclo-de-vida-del-documento-clinico.md) *Ciclo de vida del documento clínico*

Cómo un documento clínico se cierra, se prueba íntegro y se conserva. **ADR escrito el 2026-08-03** — pendiente de implementar.

| # | Requisito | Estado | Nota |
|---|---|---|---|
| A1 | **Hash de integridad al sellar** | 🚧 `ADR-0014` **D1** | **Va primero, aunque la firma se posponga.** Si se sellan documentos sin hash y la firma digital llega en dos años, los sellados en el medio **nunca podrán probar que no fueron alterados** — no hay contra qué comparar. El hash es barato (SHA-256 al sellar); la firma es cara. Desacoplarlos en el tiempo está bien; omitir el hash hoy no. |
| A2 | **Firma digital del cierre** (Ley 27269, COP 52–53) | 🚧 `ADR-0014` **D2** — diferida con punto de extensión | Sin certificado acreditado ante INDECOPI/RENIEC, la historia clínica electrónica **no sustituye al papel con valor probatorio**. Decisión: **posponer la integración, preparar la arquitectura** — generación de documento + hash + punto de extensión para un proveedor futuro. No invertir meses en IOFE antes de tener producto estable. |
| A3 | **Política de retención (20 años)** (NTS 139 **5.3.1 (6) e**) | 🚧 `ADR-0014` **D5**, corregido por [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D1** | El bloqueo real **no es el job programado, es la política**: primero se define retención → archivo → destrucción, y recién después cómo se ejecuta. Hoy no hay ninguna de las tres. Sin política, ni siquiera se puede decir qué debería hacer el scheduler. |
| A4 | **Archivo activo (5 años) / pasivo (15 más)** (NTS 139 **5.3.1 (4) k/l**) | 🚧 `ADR-0014` **D6** — **derivado**, sin job | Consecuencia de A3. Su ejecución automática necesita un job — hoy `@Scheduled` está prohibido por ArchUnit hasta cablear el contexto de tenant, pero eso es **implementación**, no la decisión. |
| A5 | **Destrucción controlada tras 20 años** (NTS 139 **5.3.1 (6) e**) | ✅ `ADR-0014` **D7** — **no se construye**: cumple por ausencia | Exige acta del Comité y doble verificación. Consecuencia de A3. |
| A6 | **Cifrado en reposo** (Ley 29733 ⚠️ sin verificar) | 🚧 **`ADR-0016` D5** — dos niveles, **una sola llave** por entorno (no por tenant) | Dos niveles: **base** (disco, backups, gestionado por infraestructura) y **dominio** (columnas sensibles: diagnósticos, notas clínicas, alergias, SOAP). **No cifrar** identificadores, fechas ni estados — destruye la indexación. ⚠️ Con llaves por tenant aparece gestión de llaves (rotación, recuperación): **perder una llave es perder la historia clínica de esa clínica**, con obligación legal de conservarla **20 años** ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D1**). |
| A7 | **Prohibición de `UPDATE`/`DELETE` por permisos de BD** (NTS 139 **5.3.1 (8) b**) | 🚧 `ADR-0014` **D4** | Hoy la inmutabilidad se garantiza **en el servicio** (rechazo server-side, testeado). El análisis normativo pide además revocar el permiso al **rol de conexión de la aplicación**: el proyecto ya tiene dos roles (`odontorisas_admin`/`odontorisas_app`), así que es viable como defensa en profundidad. Decidir en `ADR-0014`. |
| A8 | **Exportación formal del expediente** (NTS 139 **5.3.3 r** — **plazo de 5 días**) | 🚧 `ADR-0014` **D3** — mismo artefacto que el hash | Paquete con filiación, odontograma, SOAP y firmas, para entregar al paciente o a una auditoría. Ligado a A1/A2: **el documento exportado es lo que se firma**. 🔴 [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D7.5** le agrega lo que faltaba: **plazo duro de 5 días** (NTS 139 5.3.3 r). Hasta ahora era un requisito sin urgencia. |

### B. Decisiones del dominio → ✅ [`ADR-0015`](../adr/0015-identidad-del-paciente.md) · ✅ [`ADR-0016`](../adr/0016-responsabilidad-clinica.md)

Qué datos identifican a un paciente, y quién responde por cada registro.

| # | Requisito | Estado | ADR | Nota |
|---|---|---|---|---|
| B1 | **Filiación completa + Ubigeo** (NTS 139 **5.3.1 (1) b** · **5.3.3 d**) | 🚧 | [`0015`](../adr/0015-identidad-del-paciente.md) | ✅ **Decidido** (2026-08-03): la filiación completa se exige **al abrir la Historia Clínica**, no al admitir ni al sellar — el alta conserva su mínimo y `ADR-0005`/`ADR-0006` sobreviven. Ubigeo como **catálogo INEI en BD**, patrón `Cie10CatalogLoader`. Pendiente de implementar (`update-patients` + `update-clinical-history`). El nombre **se parte** en nombres · apellido paterno · apellido materno (D4). 🔴 [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D3**: el § citado (`6.1.1 a`) no existe, y la frase *"antes de permitir la apertura"* que justificaba el **momento** no es texto de la norma sino una inferencia del análisis derivado. **La decisión se mantiene** por su argumento de diseño, no por la cita. |
| B2 | **Autoría por profesional habilitado** (NTS 139 **5.3.1 (7)** · COP **art. 65**) | 🚧 **D1** | 0016 | Hoy la autoría es `userId`, sin vínculo al COP, y no hay prohibición de cuentas compartidas. Reglas del ADR: todo registro tiene autor · toda modificación tiene responsable · toda enmienda conserva el autor original · toda impresión identifica al profesional. 🔴 [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D6**: se guardan **dos** datos —número de colegiatura (estable) y estado de habilitación (mutable)—, y la autoría sellada **copia ambos**, para que una inhabilitación posterior no reescriba lo que se firmó estando habilitado. |
| B3 | **Secreto profesional** (COP **arts. 74–77**) | 🚧 **D2/D3** | 0016 | Recepción y facturación **no deben ver** SOAP, hallazgos ni CIE10. Hoy cualquier miembro del tenant ve todo — decisión explícita y temporal de `add-clinical-history`, diferida a `staff`. Con respaldo normativo deja de ser comodidad y pasa a ser incumplimiento. |
| B4 | **Versión y revocación del consentimiento** | 🚧 **D6** | 0016 | [`ADR-0007`](../adr/0007-tutor-menores.md) decidió que el tutor firma en menores y `Consent` está previsto en `treatments`. **Falta**: qué **versión** del texto se firmó y cómo se **revoca**. Sin versión no se puede probar qué aceptó el paciente si el consentimiento cambió después. |

> ⚠️ **B2 y B3 dependen de `staff`**, que hoy no existe (solo `ADMIN`/`USER`). El ADR ya está escrito; la implementación no arranca sin esa capacidad. **Eso empuja `staff` hacia arriba en el roadmap.**
>
> 🔴 **Corrección: `staff` NO necesita `ADMIN`/`DENTIST`/`RECEPTIONIST`.** Esta nota seguía pidiendo tres roles que [`ADR-0016`](../adr/0016-responsabilidad-clinica.md) **D7 eliminó**: el eje administrativo se reduce a **`ADMIN` | `MEMBER`**, y la capacidad clínica deja de ser un rol para ser una **credencial** — la **habilitación** vigente ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D6**), no la colegiatura. `DENTIST` y `RECEPTIONIST` desaparecen porque su única diferencia era quién atiende, y eso ahora lo dice la credencial.

### C. Cumplimiento y auditoría → ✅ [`ADR-0016`](../adr/0016-responsabilidad-clinica.md) *Responsabilidad clínica*

> El **cifrado en reposo** (A6) se trasladó acá desde el grupo A: su driver es la **confidencialidad** (Ley 29733 y el secreto profesional del COP), no el ciclo de vida del documento.
>
> ⚠️ **D1, D2 y D3 están bloqueados por `staff`** (modelo de roles clínicos + perfil profesional con colegiatura). El ADR define el modelo; la capacidad lo habilita. **Hasta entonces el incumplimiento sigue vigente** — es lo que más empuja a `staff` en el roadmap.

| # | Requisito | Estado | Nota |
|---|---|---|---|
| C1 | **Auditoría de acceso clínico** (Ley 29733) | 🚧 **`ADR-0016` D4** — solo lo clínico, y **bloquea** si falla | No es un log técnico de Spring: es un **registro clínico de quién leyó qué historia, de qué paciente, cuándo, desde dónde y con qué motivo**. Toda lectura y exportación de datos sensibles deja rastro no borrable. |
| C2 | Trazabilidad de transiciones de archivo (activo→pasivo) | ⚠️ | **Decisión primaria en `ADR-0014`** (depende de la política de archivo A3/A4); `ADR-0016` solo la referencia para la responsabilidad y auditoría de la transición. Sin esta aclaración quedaría sin dueño si ambos ADR se leen como excluyentes. |

### D. Pendientes de `add-dental-chart` (antes de la migración)

> ✅ **Los cuatro cerrados el 2026-08-03** contra el texto oficial de NTS 188 ([`ADR-0017`](../adr/0017-catalogo-odontograma-nts-188.md)). Tareas 0.1, 0.2 y 0.3 hechas.

| # | Requisito | Estado | Resolución |
|---|---|---|---|
| D1 | Alcance dual de `DES` | ✅ | Se registra como hallazgo **de superficie** (la verdad clínica); la sigla del recuadro se **deriva** al renderizar. El modelo no cambia (`ADR-0017` **D7**). |
| D2 | ~~`IMP` implica pieza ausente~~ | ✅ | **Falso.** El implante es un hallazgo propio y puede **coexistir** con la ausencia sin implicarla. 🔴 Esta fila citaba `6.1.16`, que es la **lesión de caries dental**; el implante es **`6.115` [sic, por 6.1.15]** — verificado el 2026-09-23 en la página 11 del texto oficial. La conclusión se mantiene, la cita estaba mal — un implante sin corona está en una pieza ausente, uno con corona no. La invariante habría rechazado registros válidos (`ADR-0017` **D7**). |
| D3 | Índice de Higiene Oral (IHO) | ✅ | **No está en NTS 188** — no es requisito del odontograma. Fuera de alcance (`ADR-0017` **D8**). |
| D4 | Registro periodontal (sondaje, furca) | ✅ | **No está en NTS 188.** Lo periodontal que sí norma (**movilidad 6.1.19**) ya está en el catálogo como hallazgo. 🔴 Esta fila decía además *"recesión 6.1.31, furca"*: **6.1.31 es prótesis dental parcial removible** en el texto oficial, y la furca está fuera de alcance; el periodontograma completo es documento aparte, fuera de alcance (`ADR-0017` **D8**). |

> ✅ **Artefactos realineados** (tarea 0.6, 2026-08-03): proposal, design, spec y tasks describen ya el modelo corregido — cinco localizaciones, catálogo `tipo`+`subtipo` con sigla, dos campos de texto, generación por evento y sin regla de contagio. `add-dental-chart` queda **sin tareas bloqueantes**.

### E. Pendientes de [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) *(NTS 139 + COP oficiales)*

> 🔴 Abiertos al leer el **texto oficial** de NTS 139 y del Código de Ética del COP el **2026-09-23**. Los ✅ de esta tabla son correcciones **ya aplicadas a los artefactos**; los 🔴 y ⚠️ son trabajo pendiente.

| # | Requisito | Estado | Resolución / dueño |
|---|---|---|---|
| E1 | **Plazo de conservación: 20 años, no 15** | ✅ corregido | `ADR-0014` D5/D6/D7 y las filas de retención ya dicen 20 ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D1**). Dos fuentes oficiales coinciden. **El único hallazgo que podía destruir datos** — y se encontró antes de existir como código. |
| E2 | **Ficha Odonto-Estomatológica incompleta** (IHO-S, placa blanda/calcificada, alta básica, odontograma final) | 🔴 **capacidad construida** | `update-clinical-history`, sin proponer. [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D2** abre el gap y lo nombra; el modelo (índice por pieza indicadora) necesita su propio diseño. |
| E3 | **§ de NTS 139 inventados** en matriz y ADR | ✅ corregido | Los siete reemplazados por los reales ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D3**). ⚠️ El requisito era correcto en los siete casos: **solo la cita era falsa**. |
| E4 | **Artículos del COP incorrectos** | ✅ parcial | Corregidos los verificables ([`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D4**). Las filas no verificadas quedan **⚠️ sin artículo**, deliberadamente: inventar un reemplazo plausible sería repetir el error. |
| E5 | **COP art. 78** — bancos de datos de salud electrónicos | ⚠️ **riesgo de producto** | [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D5** fija tres reglas; dos ya se cumplen (RLS, cifrado decidido). Lo abierto —inscripción ante la Autoridad de Protección de Datos, encargo de tratamiento, rol de *encargado* vs *titular*— es **pregunta legal, no de arquitectura**, y cruza con Ley 29733 sin verificar. **Dueño: el usuario.** |
| E6 | **La credencial clínica es la habilitación, no la colegiatura** | ✅ decidido | [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D6** refina `ADR-0016` D7. **Llega a tiempo**: `staff` no está propuesta todavía. Hace cierto el argumento *"la suspensión revoca sola"*, que con un número permanente era falso. |
| E7 | **Número de HC = DNI** vs. documento único por clínica | ⚠️ **necesita ADR** | [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D7.2**. Choca con [`ADR-0009`](../adr/0009-documento-unico-por-clinica.md) y con el modelo multi-tenant; el paciente sin DNI necesita regla propia. |
| E8 | **Consentimiento de datos personales** (Anexo 11), distinto del clínico | ⚠️ **necesita ADR** | [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D7.4**. Otro texto, otro momento, otro fundamento. Cruza con Ley 29733. |
| E9 | **Acreditación ante el RENHICE** | ⚠️ **fuera del repo** | [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D7.3**. Trámite institucional del titular. Si nunca se hace, la premisa del producto se debilita. |
| E10 | **Los análisis derivados pierden autoridad normativa** | ✅ aplicado | [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D8**. No se borran —su traducción a impacto técnico es válida y son la evidencia del error—; se degradan con advertencia y dejan de ser citables. |
| E11 | **Reverificabilidad de las citas** | ⚠️ **limitación declarada** | [`ADR-0018`](../adr/0018-cumplimiento-nts-139-y-cop.md) **D8**: los PDF oficiales **no se versionan** (terceros, licencia no analizada, peso). Se registra procedencia + fecha de consulta. Auditar exige el texto oficial a mano. Decirlo es parte de la corrección. |
| E12 | ~~Ley 29733 sin verificar~~ | ✅ **cerrado** | Texto oficial leído y versionado el 2026-09-23 junto con su reglamento vigente: [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md). Ver el **grupo F**. Queda **Ley 27269** (firma digital), ahora **menos urgente**: `ADR-0019` D4 mostró que el consentimiento no la necesita. |

### F. Pendientes de [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) *(Ley 29733 + D.S. 016-2024-JUS vigentes)*

> 🔴 Abiertos al leer los **dos textos vigentes** el **2026-09-23**. Tres están **tipificados como infracción grave** en el art. 133 del Reglamento — no son riesgos teóricos, son conductas del catálogo.

| # | Requisito | Estado | Resolución / dueño |
|---|---|---|---|
| F1 | **Documento de Seguridad** con fecha cierta e inventario de datos | 🔴 **grave** (art. 133 num. 5) | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D6** abre el gap. **Quién lo firma necesita ADR propio.** ✅ El inventario es parcialmente derivable del esquema. |
| F2 | **Procedimiento de notificación de incidentes** | 🔴 **grave** (art. 133 num. 12) | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D7**. Dentdesk avisa a la clínica **de inmediato** (art. 36); la clínica a la autoridad y al paciente en **48 h**. La obligación no se extingue al resolverlo internamente. |
| F3 | **Oficial de Datos Personales** | 🔴 | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D8**. Obligatorio por giro de negocio con datos sensibles. ✅ Admite función compartida o persona externa. ⚠️ **Traba F2**. |
| F4 | **`ADR-0016` D4 (log de accesos)** | ✅ **cerrado, con ajustes** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D5**. El art. 46.3 exige registrar **visualización** — D4 acertó. Falta agregar horas de sesión, generación continua, disponibilidad inmediata y **2 años de retención**; y **quitar** el *"no borrable"*. |
| F5 | **Baja del tenant: devolver y después borrar** | 🔴 | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D2** cierra la Open Question 3 de `ADR-0014` D7 y **asciende D3** (exportación). Tope de 2 años; *la conservación indeterminada está prohibida*. |
| F6 | **Estado de bloqueo** (rectificación / supresión en trámite) | 🔴 | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D10**. No existe, y choca con `enabledForTreatment`. Va a `update-clinical-history`. |
| F7 | **Plazos de respuesta al paciente** | 🔴 (art. 133 num. 1) | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D10**: 8 / 20 / 10 días. ⚠️ **NTS 139 manda 5 días** para la copia de la historia y gana por ser más corto. |
| F8 | **Verificación semestral de privilegios** + capacitación del personal | 🔴 / ⚠️ | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D5** (art. 46 num. 1 y 2). Obligación **operativa con reloj**, sin dueño. |
| F9 | **Respaldos con verificación de integridad** | 🔴 | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D9** (art. 48.1 num. 3). Cierra el hueco que estaba *"sin verificar y sin dueño técnico"*. |
| F10 | **Ficha para que la clínica se inscriba** en el Registro Nacional | ⚠️ **dueño: el cliente** | [`ADR-0019`](../adr/0019-proteccion-de-datos-ley-29733.md) **D11**. Dentdesk aporta la descripción técnica de las medidas de seguridad. Se escribe junto con F1. La inscripción **es gratuita**. |
| F11 | **Dónde se hospeda Dentdesk** | ⚠️ | Ningún artefacto lo dice. El art. VI num. 2 aplica la ley **con independencia de la ubicación**, así que no exime — pero hay que declararlo. |
| F12 | **Modificatorias de la Ley 29733** | ⚠️ | El texto versionado es el original de 2011; la denominación de la autoridad ya cambió. |

### No son huecos de cumplimiento

Distinción deliberada: un **hueco** es *"lo que hacemos incumple"*; esto es *"esto todavía no lo hacemos"*. Mezclarlos haría ver ocho incumplimientos donde hay seis.

| Tema | Dónde va |
|---|---|
| **Prescripción electrónica** | Funcionalidad ausente, no incumplimiento — el sistema no dice que recete. Candidata de roadmap (`vision.md §7`), con sus propias normas cuando se construya. |
| **Diagnóstico de casos estéticos sin CIE10** | Limitación del catálogo CIE10 (clasifica enfermedad, no procedimiento). Anotado en `vision.md §3`. |
