# Matriz de trazabilidad normativa

Mapea **cada requisito normativo** que el sistema debe cumplir con **la decisión que lo resuelve** y **el artefacto que lo implementa**. Sirve para responder, ante una auditoría de SUSALUD o del MINSA, *"¿cómo cumple el sistema este requisito?"* siguiendo un rastro verificable en vez de un grep.

## Qué NO es este documento

**No es un resumen de las normas.** El análisis por norma vive en los documentos hermanos de esta carpeta (`nts-139-history-clinical.md`, `nts-188-odontogram.md`, `nts-150-to-188-transition.md`, `cop-code-of-ethics.md`, `legal-retention.md`). Acá solo se **enlaza** requisito ↔ decisión ↔ artefacto.

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

## Cómo mantenerla

- **Al escribir un ADR** con motivación normativa: agregar o actualizar su fila (`docs/adr/README.md`, dimensión 1).
- **Al archivar un change**: reemplazar `pendiente` por el requirement/scenario real y actualizar el estado.
- **Nunca poner "Sí"** en la columna de artefacto. Un "Sí" es una luz verde que nadie verifica — el mismo modo de falla que dejó reglas ArchUnit en verde durante meses sin comprobar nada (`docs/testing.md §5`). Apuntar siempre al **nombre concreto** del requirement o del scenario.

## Normas de referencia

| Norma | Instrumento | Ámbito |
|---|---|---|
| **NTS N°139-MINSA/2018/DGAIN** | RM N°214-2018/MINSA | Gestión de la Historia Clínica |
| **NTS N°188-MINSA/DGIESP-2022** | RM N°559-2022/MINSA (El Peruano, 29/07/2022) | Uso del Odontograma — **deroga** NTS N°150 (art. 2 deroga la RM N°595-2019) |
| **Ley N°29733** + D.S. N°003-2013-JUS | — | Protección de Datos Personales |
| **Ley N°27269** + D.S. N°052-2008-PCM | — | Firmas y Certificados Digitales |
| **Código de Ética y Deontología del COP** | Ley N°27878, D.S. N°016-2005-SA | Ejercicio profesional del Cirujano Dentista |

**Estado:** ✅ construido y testeado · 🚧 decidido, no construido · ⚠️ sin decisión · 🔴 **gap sobre capacidad ya construida**

---

## Historia clínica

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| Registro secuencial y cronológico de la atención | NTS 139 | **5.4.1 f)** | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | `clinical-history` → *Información inicial sellada tras COMPLETED* | ✅ |
| Prohibición de enmiendas, raspaduras o destrucción de folios | NTS 139 | **5.4.1 i)** | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Rechazar la edición de la información sellada aun por API directa* | ✅ |
| El diagnóstico lleva tipo obligatorio: presuntivo / definitivo / repetido | MINSA HIS | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Rechazar un diagnóstico sin tipo (P/D/R)* | ✅ |
| Cada diagnóstico registra fecha/hora y profesional, de forma inmutable | MINSA HIS | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Agregar un diagnóstico general* | ✅ |
| El diagnóstico usa el catálogo CIE10 oficial, respetando su jerarquía | MINSA (CIE10) | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Buscar un código navegando la jerarquía* | ✅ |
| El menor requiere representante legal que autorice procedimientos | COP / Código Civil | *pend.* | [`ADR-0007`](../adr/0007-tutor-menores.md) | `patients` → *responsable obligatorio para menores* | ✅ datos · 🚧 consentimiento (`treatments`) |
| **Filiación completa y obligatoria, incluido Ubigeo de nacimiento y procedencia** | NTS 139 | **6.1.1 a)** | ⚠️ **choca con [`ADR-0005`](../adr/0005-registro-minimo-paciente.md)** | — | 🔴 **ver gaps** |
| Evolución estructurada en SOAP (Subjetivo/Objetivo/Análisis/Plan) | NTS 139 | **6.1.4** | ⚠️ sin ADR | `update-clinical-history` (fase 2) | ⚠️ |
| Enmienda como asiento nuevo vinculado, sin alterar el original | NTS 139 | **5.4.1 i)** | ⚠️ sin ADR — diferido en `ADR-0011` | `update-clinical-history` (fase 2) | ⚠️ |

## Odontograma

[`ADR-0012`](../adr/0012-odontograma-libro-de-asientos.md) + [`ADR-0013`](../adr/0013-odontograma-nts-188.md). `add-dental-chart` está **propuesto**, no implementado: los requirements viven en `openspec/changes/add-dental-chart/specs/`.

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| Estructura de dentición permanente y decidua en notación **FDI** | NTS 188 | **6.1** | `add-dental-chart` design **D7** | `dental-chart` → *Registro de hallazgos…* · scenario *Rechazar una pieza inexistente* | 🚧 |
| **Código binario rojo/azul** obligatorio, separando patología de restauración | NTS 188 | **6.2** | `ADR-0013` **D2** + **D5** | `dental-chart` → *Catálogo cerrado de condiciones, alineado a la norma vigente* | 🚧 |
| Siglas oficiales de tres letras (`IMP`, `MAC`, `MIC`, `DES`) y letra `E` | NTS 188 | **Anexo N°01** | `ADR-0013` **D3** | `dental-chart` → *Catálogo cerrado…* | 🚧 |
| Movilidad en arábigos `M1`/`M2`/`M3`, en **azul** (ya no romanos rojos) | NTS 188 | **Anexo N°01** | `ADR-0013` **D3** | `dental-chart` → scenarios de grado | 🚧 |
| Giroversión: color **condicional** según patología activa de la pieza | NTS 188 | **Anexo N°01** (UP-004) | `ADR-0012` **D2** (regla de contagio) | `dental-chart` → regla de renderizado | 🚧 |
| Sellante en mal estado **coexiste** con caries adyacente en la misma cara | NTS 188 | **Anexo N°01** | `ADR-0013` **D2** | scenario *Una restauración y una patología coexisten* | 🚧 |
| El odontograma es anexo de la historia clínica y se sella con la admisión | NTS 139 | **6.1.1** | `ADR-0012` **D5** | `clinical-history` → *Sellado a COMPLETED…* (MODIFICADO) | 🚧 |
| El odontograma inicial es inalterable | NTS 139 | **5.4.1 i)** | `ADR-0012` **D4** | `dental-chart` → *Odontograma sellado e inmutable* | 🚧 |
| Sección de Especificaciones / Observaciones obligatoria | NTS 188 | **Anexo N°01** | `ADR-0012` *Modelo resultante* + `ADR-0013` **D4** | `dental-chart` → *Especificaciones del odontograma* | 🚧 |
| Registro por superficie dental (extensión auditable) | NTS 188 / SUSALUD | **Anexo N°01** | `ADR-0012` **D8** | `dental-chart` → *Registro de hallazgos con localización precisa* | 🚧 |
| Cierre transaccional atómico de evolución **y** odontograma del día | NTS 139 | **5.4.1 f)** (HC-007) | `ADR-0012` **D5** | pendiente — `update-dental-chart` (fase 2) | 🚧 |
| Bloqueo del expediente con atención anterior sin cerrar | NTS 139 | **5.4.1 f)** (HC-006) | `ADR-0012` **D7** | pendiente — `update-dental-chart` (fase 2) | 🚧 |
| Odontograma de evolución comparable entre fechas | NTS 188 | **6.1** | `ADR-0012` **D3** | pendiente — `update-dental-chart` (fase 2) | 🚧 |

## Retención y ciclo de vida

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| Conservación íntegra **15 años** desde la última atención | NTS 139 | **6.6.1 a)** | ⚠️ sin ADR | — | 🔴 **ver gaps** |
| Archivo **activo** (≤5 años) y **pasivo** (>5 años) | NTS 139 | **6.6.1 / 6.6.2** | ⚠️ sin ADR | — | ⚠️ |
| Destrucción solo tras 15 años y con acta del Comité | NTS 139 | **6.6.1 e)** | ⚠️ sin ADR | — | ⚠️ |
| Respaldos cifrados y recuperables (datos sensibles) | Ley 29733 | D.S. 003-2013-JUS | ⚠️ sin ADR — infraestructura | — | ⚠️ |

## Seguridad, secreto profesional y no repudio

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| Aislamiento entre clínicas; la historia es privada del paciente | Ley 29733 / NTS 139 | — | [`ADR-0004`](../adr/0004-el-paciente-pertenece-a-una-clinica.md) | `multi-tenancy` + test de aislamiento **obligatorio** (`docs/testing.md §5`) | ✅ |
| **Secreto profesional**: recepción no accede a SOAP, hallazgos ni CIE10 | COP | **Sec. II, Cap. V, Arts. 31–36** | ⚠️ diferido a `staff` | — | 🔴 **ver gaps** |
| **Autoría por colegiatura COP**, sin cuentas compartidas | COP | **Sec. III, Cap. I, Art. 42** | ⚠️ diferido a `staff` | autoría hoy por `userId` | 🔴 **ver gaps** |
| **Firma digital** del cierre con certificado acreditado | Ley 27269 / COP | **Sec. III, Cap. II, Arts. 52–53** | ⚠️ sin ADR | — | 🔴 **ver gaps** |
| Integridad: prohibido alterar, falsear o destruir registros | COP | **Sec. III, Cap. II, Art. 54** | `ADR-0011` + `ADR-0012` **D3** | sellado server-side, libro de asientos | ✅ parcial |
| **Cifrado en reposo** de datos clínicos | Ley 29733 | D.S. 003-2013-JUS | ⚠️ sin ADR | — | 🔴 **ver gaps** |
| **Log de accesos** de lectura y exportación de datos sensibles | Ley 29733 | D.S. 003-2013-JUS | ⚠️ sin ADR | — | 🔴 **ver gaps** |

---

## Huecos abiertos

Ordenados por gravedad. Los 🔴 afectan **capacidades ya construidas y en uso**.

| # | Requisito | Estado | Nota |
|---|---|---|---|
| 1 | **Firma digital del cierre clínico** (Ley 27269, COP 52–53) | 🔴 | Sin firma con certificado acreditado ante INDECOPI/RENIEC, la historia clínica electrónica **no sustituye al papel con pleno valor probatorio**. Afecta el sellado de `clinical-history`, **ya construido**. Es el gap de mayor impacto legal: todo lo demás puede ser correcto y aun así el documento no probar nada en juicio. Amerita ADR propio. |
| 2 | **Filiación completa obligatoria + Ubigeo** (NTS 139 6.1.1 a) | 🔴 | La norma exige documento, sexo, fecha de nacimiento y **Ubigeo de nacimiento y procedencia** como obligatorios. `patients` está construida con documento **opcional** ([`ADR-0005`](../adr/0005-registro-minimo-paciente.md), el caso del "bebé sin documento") y **no tiene ubigeo en absoluto**. Esta evidencia **resuelve la Open Question** que `vision.md §3` dejaba abierta sobre endurecer los datos mínimos: la norma lo exige. Requiere `update-patients` + migración. |
| 3 | **Cifrado en reposo de datos clínicos** (Ley 29733) | 🔴 | Texto libre clínico, diagnósticos y antecedentes se guardan en claro. Un respaldo filtrado expone historias completas. Transversal: toca `patients`, `clinical-history` y todo lo que venga. |
| 4 | **Secreto profesional: RBAC clínico** (COP 31–36) | 🔴 | Hoy **cualquier miembro del tenant** puede leer y escribir todo — decisión explícita y temporal documentada en `add-clinical-history`, diferida a `staff`. Con respaldo normativo deja de ser una comodidad y pasa a ser incumplimiento: la recepción no debe ver SOAP, hallazgos ni CIE10. Sube la prioridad de `staff` en el roadmap. |
| 5 | **Autoría por colegiatura COP** (COP 42) | 🔴 | La autoría se registra por `userId`, sin vínculo al COP. Un documento sellado firmado por una identidad técnica puede no bastar ante auditoría. Va con `staff`, junto al #4. |
| 6 | **Log de accesos de lectura** (Ley 29733) | 🔴 | Cada consulta y exportación de datos sensibles debe dejar rastro no borrable (usuario, IP, fecha, pacientes expuestos). No existe. |
| 7 | **Retención 15 años + archivo activo/pasivo** (NTS 139 6.6.1/6.6.2) | ⚠️ | Sin política, sin estado de archivo, sin bloqueo de purga. El paso automático activo→pasivo a los 5 años requiere un job programado — hoy **bloqueado por la regla ArchUnit** que prohíbe `@Scheduled` hasta cablear el contexto de tenant. |
| 8 | **Índice de Higiene Oral (IHO)** | ⚠️ | No aparece en NTS 188 según los documentos de esta carpeta. **Verificar si pertenece a NTS 139 o queda fuera** antes de decidir (`add-dental-chart` tarea 0.2). |
| 9 | **Registro periodontal (sondaje, furca)** | ⚠️ | Sin decisión sobre dónde vive (`add-dental-chart` tarea 0.3). |
| 10 | **Diagnóstico de casos estéticos sin CIE10** | ⚠️ | CIE10 clasifica enfermedad, no procedimiento. Anotado en `vision.md §3`. |
| 11 | **Alcance dual de `DES`** | ⚠️ | NTS 188 pide la sigla en el **recuadro de la pieza** *y* pintar la **cara afectada**. El modelo actual tiene alcance de valor único (superficie **o** pieza **o** entre piezas). Resolver antes de la migración (`add-dental-chart` tarea 0.1). |
| 12 | **`IMP` implica pieza ausente** | ⚠️ | NTS 188 vincula el implante a la pieza reemplazada e inhabilita sus caras. El modelo tiene la invariante para *ausente* pero no dice que `IMP` la implique. Resolver en el catálogo (tarea 0.1). |
