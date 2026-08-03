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

Agrupados por **qué documento los resuelve**, no por tema. Un gap sin dueño no se cierra: saber si le toca un ADR, un spec o una política es la mitad del trabajo.

Los 🔴 afectan **capacidades ya construidas y en uso**. Los ⚠️ son decisiones pendientes sin incumplimiento activo.

> **Plan acordado (2026-08-03):** en vez de abrir seis changes paralelos, **tres ADR transversales** cubren la mayoría de estos huecos manteniendo coherencia arquitectónica. Ninguno está escrito todavía.

### A. Decisiones de arquitectura → `ADR-0014` *Clinical Document Lifecycle*

Cómo un documento clínico se cierra, se prueba íntegro y se conserva.

| # | Requisito | Estado | Nota |
|---|---|---|---|
| A1 | **Hash de integridad al sellar** | 🔴 | **Va primero, aunque la firma se posponga.** Si se sellan documentos sin hash y la firma digital llega en dos años, los sellados en el medio **nunca podrán probar que no fueron alterados** — no hay contra qué comparar. El hash es barato (SHA-256 al sellar); la firma es cara. Desacoplarlos en el tiempo está bien; omitir el hash hoy no. |
| A2 | **Firma digital del cierre** (Ley 27269, COP 52–53) | 🔴 | Sin certificado acreditado ante INDECOPI/RENIEC, la historia clínica electrónica **no sustituye al papel con valor probatorio**. Decisión: **posponer la integración, preparar la arquitectura** — generación de documento + hash + punto de extensión para un proveedor futuro. No invertir meses en IOFE antes de tener producto estable. |
| A3 | **Política de retención (15 años)** (NTS 139 6.6.1 a) | 🔴 | El bloqueo real **no es el job programado, es la política**: primero se define retención → archivo → destrucción, y recién después cómo se ejecuta. Hoy no hay ninguna de las tres. Sin política, ni siquiera se puede decir qué debería hacer el scheduler. |
| A4 | **Archivo activo (≤5 años) / pasivo (>5)** (NTS 139 6.6.1/6.6.2) | ⚠️ | Consecuencia de A3. Su ejecución automática necesita un job — hoy `@Scheduled` está prohibido por ArchUnit hasta cablear el contexto de tenant, pero eso es **implementación**, no la decisión. |
| A5 | **Destrucción controlada tras 15 años** (NTS 139 6.6.1 e) | ⚠️ | Exige acta del Comité y doble verificación. Consecuencia de A3. |
| A6 | **Cifrado en reposo** (Ley 29733) | 🔴 | Dos niveles: **base** (disco, backups, gestionado por infraestructura) y **dominio** (columnas sensibles: diagnósticos, notas clínicas, alergias, SOAP). **No cifrar** identificadores, fechas ni estados — destruye la indexación. ⚠️ Con llaves por tenant aparece gestión de llaves (rotación, recuperación): **perder una llave es perder la historia clínica de esa clínica**, con obligación legal de conservarla 15 años. |

### B. Decisiones del dominio → `ADR-0015` *Patient Identity* · `ADR-0016` *Clinical Accountability*

Qué datos identifican a un paciente, y quién responde por cada registro.

| # | Requisito | Estado | ADR | Nota |
|---|---|---|---|---|
| B1 | **Filiación completa + Ubigeo** (NTS 139 6.1.1 a) | 🔴 | 0015 | La norma exige documento, sexo, fecha de nacimiento y **Ubigeo de nacimiento y procedencia**. `patients` está construida con documento **opcional** ([`ADR-0005`](../adr/0005-registro-minimo-paciente.md), el caso del "bebé sin documento") y **sin ubigeo**. **No es un bug: es una decisión arquitectónica pendiente** que contradice un ADR aceptado. Resuelve además la Open Question que `vision.md §3` arrastraba sobre endurecer los datos mínimos. |
| B2 | **Autoría por colegiatura COP** (COP art. 42) | 🔴 | 0016 | Hoy la autoría es `userId`, sin vínculo al COP, y no hay prohibición de cuentas compartidas. Reglas del ADR: todo registro tiene autor · toda modificación tiene responsable · toda enmienda conserva el autor original · toda impresión identifica al profesional. |
| B3 | **Secreto profesional** (COP arts. 31–36) | 🔴 | 0016 | Recepción y facturación **no deben ver** SOAP, hallazgos ni CIE10. Hoy cualquier miembro del tenant ve todo — decisión explícita y temporal de `add-clinical-history`, diferida a `staff`. Con respaldo normativo deja de ser comodidad y pasa a ser incumplimiento. |
| B4 | **Versión y revocación del consentimiento** | ⚠️ | 0016 | [`ADR-0007`](../adr/0007-tutor-menores.md) decidió que el tutor firma en menores y `Consent` está previsto en `treatments`. **Falta**: qué **versión** del texto se firmó y cómo se **revoca**. Sin versión no se puede probar qué aceptó el paciente si el consentimiento cambió después. |

> ⚠️ **B2 y B3 dependen de `staff`**: necesitan el modelo de roles real (`ADMIN`/`DENTIST`/`RECEPTIONIST`), que hoy no existe (solo `ADMIN`/`USER`). El ADR se puede escribir ya; la implementación no arranca sin esa capacidad. **Eso empuja `staff` hacia arriba en el roadmap.**

### C. Cumplimiento y auditoría → `ADR-0016` *Clinical Accountability*

| # | Requisito | Estado | Nota |
|---|---|---|---|
| C1 | **Auditoría de acceso clínico** (Ley 29733) | 🔴 | No es un log técnico de Spring: es un **registro clínico de quién leyó qué historia, de qué paciente, cuándo, desde dónde y con qué motivo**. Toda lectura y exportación de datos sensibles deja rastro no borrable. |
| C2 | Trazabilidad de transiciones de archivo (activo→pasivo) | ⚠️ | Evidencia ante SUSALUD de que la conservación se aplica en los plazos exactos. Depende de A3/A4. |

### D. Pendientes de `add-dental-chart` (antes de la migración)

| # | Requisito | Estado | Nota |
|---|---|---|---|
| D1 | **Alcance dual de `DES`** | ⚠️ | NTS 188 pide la sigla en el **recuadro de la pieza** *y* pintar la **cara afectada**. El modelo tiene alcance de valor único. Tarea 0.1. |
| D2 | **`IMP` implica pieza ausente** | ⚠️ | La invariante existe para *ausente*; falta que `IMP` la dispare. Tarea 0.1. |
| D3 | **Índice de Higiene Oral (IHO)** | ⚠️ | No aparece en NTS 188. Verificar si pertenece a NTS 139 o queda fuera. Tarea 0.2. |
| D4 | **Registro periodontal (sondaje, furca)** | ⚠️ | Sin decisión sobre dónde vive. En la práctica es un periodontograma aparte. Tarea 0.3. |

### No son huecos de cumplimiento

Distinción deliberada: un **hueco** es *"lo que hacemos incumple"*; esto es *"esto todavía no lo hacemos"*. Mezclarlos haría ver ocho incumplimientos donde hay seis.

| Tema | Dónde va |
|---|---|
| **Prescripción electrónica** | Funcionalidad ausente, no incumplimiento — el sistema no dice que recete. Candidata de roadmap (`vision.md §7`), con sus propias normas cuando se construya. |
| **Diagnóstico de casos estéticos sin CIE10** | Limitación del catálogo CIE10 (clasifica enfermedad, no procedimiento). Anotado en `vision.md §3`. |
