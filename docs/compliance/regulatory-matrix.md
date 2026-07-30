# Matriz de trazabilidad normativa

Mapea **cada requisito normativo** que el sistema debe cumplir con **la decisión que lo resuelve** y **el artefacto que lo implementa**. Sirve para responder, ante una auditoría de SUSALUD o del MINSA, *"¿cómo cumple el sistema este requisito?"* siguiendo un rastro verificable en vez de un grep.

## Qué NO es este documento

**No es un resumen de las normas.** Un resumen local de una regulación externa se desactualiza en silencio: la norma se actualiza en el MINSA, la copia no, y alguien construye contra un texto vencido creyendo que está vigente. Acá solo se cita el **fragmento puntual que justificó una decisión**, con su fecha de consulta.

**No es un segundo hogar para las decisiones.** El *por qué* vive en los ADR; el *qué construir*, en los changes de OpenSpec. Esta matriz solo **enlaza** lo que ya existe.

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

Los cuatro eslabones de la derecha ya son convención del proyecto. Esta matriz aporta el primero.

## Cómo mantenerla

- **Al escribir un ADR** con una motivación normativa: agregar o actualizar su fila (`docs/adr/README.md`, dimensión 1).
- **Al archivar un change**: reemplazar `pendiente` por el requirement/scenario real y actualizar el estado.
- **Nunca poner "Sí"** en la columna de artefacto. Un "Sí" es una luz verde que nadie verifica — el mismo modo de falla que dejó reglas ArchUnit en verde durante meses sin comprobar nada (`docs/testing.md §5`). Apuntar siempre al **nombre concreto** del requirement o del scenario, que es comprobable en segundos.

> ⚠️ **Columna de artículo (§) pendiente.** Las filas de abajo se derivaron de los ADR ya escritos, que citan las normas por su designación general (NTS N°139-MINSA, NTS N°150-MINSA/2019/DGIESP, estructura MINSA HIS) pero **no por artículo**. Los números de artículo los debe completar quien tenga los textos a la vista — **inventarlos en un documento de cumplimiento sería inducir a error a un auditor**, así que se dejan vacíos a propósito.

**Estado:** ✅ construido y testeado · 🚧 decidido, no construido · ⚠️ sin decisión

---

## Historia clínica

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| La historia clínica es un documento sellado: lo registrado no se reescribe | NTS 139 | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | `clinical-history` → *Información inicial sellada tras COMPLETED* | ✅ |
| El bloqueo del documento sellado no puede depender del cliente | NTS 139 | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Rechazar la edición de la información sellada aun por API directa* | ✅ |
| El diagnóstico lleva tipo obligatorio: presuntivo / definitivo / repetido | MINSA HIS | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Rechazar un diagnóstico sin tipo (P/D/R)* | ✅ |
| Cada diagnóstico registra fecha/hora y profesional, de forma inmutable | MINSA HIS | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Agregar un diagnóstico general* | ✅ |
| El diagnóstico usa el catálogo CIE10 oficial, respetando su jerarquía | MINSA (CIE10) | *pend.* | [`ADR-0011`](../adr/0011-historia-clinica-documento-inicial.md) | Scenario *Buscar un código navegando la jerarquía* | ✅ |
| El menor requiere representante legal que autorice procedimientos | Código Civil / COP | *pend.* | [`ADR-0007`](../adr/0007-tutor-menores.md) | `patients` → *responsable obligatorio para menores* | ✅ datos · 🚧 consentimiento (va en `treatments`) |
| Evolución cronológica de la atención (nota SOAP diaria) | NTS 139 | *pend.* | — anotado en `vision.md §3` | `update-clinical-history` (fase 2) | ⚠️ sin ADR |

## Odontograma

Todas 🚧: [`ADR-0012`](../adr/0012-odontograma-libro-de-asientos.md) está aceptado, `add-dental-chart` aún no se propone.

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| El odontograma es anexo obligatorio de la historia clínica y se sella junto con la admisión | NTS 139 | *pend.* | `ADR-0012` **D5** | pendiente — `add-dental-chart` | 🚧 |
| El odontograma inicial es inalterable: evidencia estática de cómo llegó el paciente | NTS 150 | *pend.* | `ADR-0012` **D4** | pendiente — `add-dental-chart` | 🚧 |
| El plan de tratamiento **no** se registra sobre el odontograma inicial | NTS 150 | *pend.* | `ADR-0012` **D1** | pendiente — `add-dental-chart` | 🚧 |
| Código de color: rojo = requiere intervención · azul = sin patología activa | NTS 150 | *pend.* | `ADR-0012` **D2** | pendiente — `add-dental-chart` | 🚧 |
| Sección de Especificaciones / Observaciones obligatoria | NTS 150 | *pend.* | `ADR-0012` *Modelo resultante* | pendiente — `add-dental-chart` | 🚧 |
| Registro por superficie dental, no por pieza completa (extensión auditable) | NTS 150 / SUSALUD | *pend.* | `ADR-0012` **D8** | pendiente — `add-dental-chart` | 🚧 |
| Condiciones registrables: supernumerario, diastema, movilidad, erupción, extrusión, intrusión, giroversión | NTS 150 | *pend.* | `ADR-0012` **D8** + catálogo `FindingType` | pendiente — `add-dental-chart` | 🚧 |
| Odontograma de evolución comparable entre fechas | NTS 150 | *pend.* | `ADR-0012` **D3** | pendiente — `update-dental-chart` (fase 2) | 🚧 |
| El tramo de evolución se cierra y congela al terminar la atención | NTS 139 | *pend.* | `ADR-0012` **D6** | pendiente — `update-dental-chart` (fase 2) | 🚧 |
| Secuencia cronológica ininterrumpida del expediente del paciente | NTS 139 | *pend.* | `ADR-0012` **D7** | pendiente — `update-dental-chart` (fase 2) | 🚧 |

## Aislamiento y datos personales

| Requisito normativo | Norma | § | Decisión | Requirement / Scenario | Estado |
|---|---|---|---|---|---|
| La historia clínica es individual y privada de cada paciente; una clínica no accede a los datos de otra | Ley 29733 (datos personales) / NTS 139 | *pend.* | [`ADR-0004`](../adr/0004-el-paciente-pertenece-a-una-clinica.md) | `multi-tenancy` + test de aislamiento **obligatorio** por capacidad (`docs/testing.md §5`) | ✅ |

---

## Huecos abiertos

Requisitos normativos **sin decisión tomada**. Esta sección es el principal valor de la matriz: hace visible lo que de otro modo queda implícito.

| Requisito | Estado | Nota |
|---|---|---|
| **Retención de 15 años desde la última atención + archivo pasivo, sin borrado** | ⚠️ **sin ADR ni change** | Anotado solo como pendiente en `vision.md §3`. Hoy no existe endpoint de borrado de historia clínica (no hay riesgo de purga accidental), pero tampoco hay política de retención, ni estado de archivo, ni nada que impida un borrado futuro. **Es el hueco más grande de la matriz.** |
| **Vínculo del profesional con su colegiatura COP** | ⚠️ diferido | `ADR-0011` y `ADR-0012` registran autoría por `userId`, y ambos anotan que el vínculo formal al COP llega con `staff` (fila 2 del roadmap, no propuesta). Un documento clínico firmado por un `userId` sin colegiatura puede no bastar ante auditoría. |
| **Consentimiento informado firmado por tratamiento** | 🚧 planificado | `ADR-0007` decidió que el tutor lo firma en menores; la entidad `Consent` está prevista en `treatments` (`docs/domain.md`), capacidad no construida. |
| **Evolución clínica SOAP (Subjetivo/Objetivo/Análisis/Plan)** | ⚠️ sin ADR | Anotado en `vision.md §3` como `update-clinical-history` fase 2, junto con el odontograma de evolución. Sin decisión formalizada. |
| **Diagnóstico de casos estéticos sin código CIE10** | ⚠️ sin decisión | CIE10 clasifica enfermedad, no procedimiento. Anotado en `vision.md §3`; tres caminos posibles, ninguno elegido. |
