# ADR-0015: Filiación completa exigida al abrir la Historia Clínica

- **Estado:** Aceptado (decisión); **pendiente de implementar** — requiere `update-patients` + `update-clinical-history`
- **Fecha:** 2026-08-03
- **Decisores:** David Velarde Alvarado
- **Relacionado:** completa [`ADR-0005`](0005-registro-minimo-paciente.md) (no lo deroga) · aplica el criterio de [`ADR-0006`](0006-validaciones-clinicas-por-etapa.md) · no toca [`ADR-0004`](0004-el-paciente-pertenece-a-una-clinica.md) ni [`ADR-0009`](0009-documento-unico-por-clinica.md)

## Contexto

La **NTS N°139-MINSA/2018/DGAIN, numeral 6.1.1 literal a) ("Filiación")** exige que toda historia clínica cuente de forma mandatoria con un conjunto de datos de identificación: nombres, apellidos, tipo y número de documento, sexo, fecha de nacimiento, y las cadenas de **Ubigeo de nacimiento y de procedencia**.

Lo construido diverge en dos puntos:

- **`patients` tiene el documento opcional.** Fue una decisión deliberada de [`ADR-0005`](0005-registro-minimo-paciente.md): no frenar la admisión en recepción, y admitir el caso real del recién nacido sin DNI. Está construido y testeado.
- **El Ubigeo no existe** en ninguna parte del modelo.

`docs/vision.md §3` arrastraba esto como una Open Question sin resolver (*"endurecer datos mínimos del alta"*), señalando que el propio `ADR-0005` era internamente inconsistente y que faltaba decisión humana. **La evidencia normativa la resuelve**: los datos son obligatorios. Lo que quedaba por decidir es **en qué momento** se exigen.

## Decisión

### D1 — La filiación completa se exige al **abrir la Historia Clínica**, no al admitir al paciente

El alta de paciente **conserva su mínimo actual** (nombre y fecha de nacimiento). La **Historia Clínica no se puede crear** si la filiación del paciente está incompleta.

```
ALTA (recepción)          APERTURA DE LA HC          SELLADO DE LA HC
┌────────────────┐        ┌────────────────────┐     ┌──────────────────┐
│ nombres        │        │ + documento        │     │ checklist        │
│ apellido pat.  │  ───▶  │ + sexo             │ ──▶ │ clínico          │
│ fecha nac.     │        │ + ubigeo nac./proc.│     │ (sin cambios)    │
│ documento  ○   │        └────────────────────┘     └──────────────────┘
└────────────────┘          sin filiación completa
  admisión rápida           no se abre el documento
  el bebé sin DNI entra
```

**Por qué en la apertura y no en los extremos.** El análisis normativo del repo ([`nts-139-history-clinical.md`](../compliance/nts-139-history-clinical.md), numeral 6.1.1 a) lo sitúa ahí con precisión: *"el formulario debe obligar a completar la totalidad de los campos demográficos **antes de permitir la apertura de la historia clínica**"*.

- **No en el alta**: la norma regula la historia clínica, no el acto administrativo de anotar a alguien en la agenda. Exigirlo ahí frena la recepción y mata el caso del recién nacido sin DNI.
- **No en el sellado**: sería más tarde de lo que la norma pide. Una historia abierta ya es un documento clínico en curso.

Así **`ADR-0005` sobrevive íntegro** (el alta conserva su mínimo) y se cumple el momento que la norma señala. Sigue aplicando el criterio de [`ADR-0006`](0006-validaciones-clinicas-por-etapa.md) —*"validaciones según la etapa"*—, solo que la etapa correcta resultó ser la apertura.

> 💡 **Efecto secundario valioso**: al no tocar el checklist de sellado, este ADR **no colisiona** con `add-dental-chart`, que sí lo modifica. Son requirements distintos de la misma capacidad (*creación* vs. *sellado*) y pueden implementarse por separado sin que uno pise al otro.

**Caso transitorio**: no hay Historias Clínicas abiertas en producción (el sistema no está desplegado). Si al implementar existiera alguna en `DRAFT` con filiación incompleta, se completa antes de sellarla — es una contingencia de migración, no un requirement del spec.

### D2 — El Ubigeo es un catálogo en base de datos, no texto libre

Se persisten `ubigeo_nacimiento` y `ubigeo_procedencia` como referencias a un **catálogo INEI** (código de 6 dígitos, jerárquico: departamento → provincia → distrito, ~1 900 distritos).

Mismo patrón, ya construido y probado, que el **catálogo CIE10-MINSA**: tabla propia, carga inicial idempotente desde un archivo de datos, verificación por conteo, transaccional, **sin `organization_id`** (es dato de referencia global, no de negocio de una clínica). Se replica `Cie10CatalogLoader`, no se inventa un mecanismo.

*Alternativa descartada:* guardar solo el código con validación de formato. Más barato hoy, pero acepta códigos inexistentes (`999999` pasaría), impide que la interfaz ofrezca búsqueda por nombre de distrito —el odontólogo tendría que saberse el código— y produce reportes con ubigeos inválidos, que es justo lo que la norma quiere evitar.

### D3 — Qué cuenta como "filiación completa"

| Campo | Hoy | Tras este ADR |
|---|---|---|
| Nombres · apellido paterno | ✅ obligatorio en el alta | **partido** (D4) — reemplaza a `fullName` |
| Apellido materno | ⚪ opcional | **nuevo** (D4) — opcional por extranjeros con un solo apellido |
| Fecha de nacimiento | ✅ obligatorio en el alta | sin cambios |
| Tipo + número de documento | ⚪ opcional, emparejados | **exigido al abrir la HC** |
| Sexo | ⚪ opcional | **exigido al abrir la HC** |
| Ubigeo de nacimiento | ❌ no existe | **nuevo, exigido al abrir la HC** |
| Ubigeo de procedencia | ❌ no existe | **nuevo, exigido al abrir la HC** |

El **documento sigue siendo único por clínica** y emparejado con su tipo: `ADR-0004` y `ADR-0009` no se tocan. Lo que cambia es cuándo se vuelve obligatorio, no su semántica.

### D4 — El nombre se parte: nombres · apellido paterno · apellido materno

`fullName` como campo único **se retira**. El registro civil peruano distingue **apellido paterno y materno** (RENIEC), la norma lista *"nombres, apellidos"* como elementos separados, y cualquier reporte o integración oficial los necesita así.

**Reconstruirlos desde un campo único es imposible de forma fiable** — *"María de los Ángeles Vargas Llosa"* no se parte por espacios, y adivinar mal produce datos incorrectos en silencio, que es peor que no tenerlos.

| Campo | Obligatorio | Nota |
|---|---|---|
| `nombres` | ✅ | uno o varios, en un campo |
| `apellidoPaterno` | ✅ | |
| `apellidoMaterno` | ⚪ **opcional, sin condición** | ver el criterio abajo |

**Por qué `apellidoMaterno` es opcional, y por qué sin condicionarlo.** Hay dos casos legítimos con un solo apellido: extranjeros (`CE`/pasaporte) y **peruanos reconocidos por un solo progenitor**, que en el registro civil llevan un apellido. Se consideró condicionar la exigencia al tipo de documento (obligatorio si `DNI`, opcional si `CE`/pasaporte), pero **el segundo caso lo invalida**: un peruano con DNI puede tener un solo apellido, y la regla lo bloquearía.

Al no poder verificarse contra ningún dato del sistema, se declara **opcional sin condición**. ⚠️ **Esto es una decisión de dominio, no cumplimiento literal**: la norma lista *"apellidos"* sin precisar cuántos. Si un auditor exigiera ambos, la respuesta es que el modelo admite registrar la realidad y no fuerza a inventar un apellido inexistente — pero conviene tenerlo consciente, no descubrirlo en la auditoría.

**Migración sin inventar datos.** Los pacientes ya registrados conservan su `fullName` tal cual; los campos nuevos nacen vacíos. **No se intenta partir automáticamente.**

```
Paciente existente          Paciente nuevo
┌────────────────────┐      ┌────────────────────┐
│ fullName  (legacy) │      │ nombres            │
│ nombres        ∅   │      │ apellidoPaterno    │
│ apellidoPaterno ∅  │      │ apellidoMaterno?   │
└────────────────────┘      └────────────────────┘
   se muestra el legacy       se muestran los nuevos
   completar antes de sellar  fullName no se usa
```

**Fuente de verdad durante la transición** (los cuatro puntos que hay que respetar mientras coexistan):

| Operación | Regla |
|---|---|
| **Lectura / visualización** | Si los campos nuevos están presentes, mandan. Si no, se muestra `fullName`. Nunca se mezclan. |
| **Búsqueda** | Filtra sobre los tres campos nuevos **y** sobre `fullName`, para no perder pacientes sin migrar. |
| **Edición** | Al guardar un paciente legacy, los campos nuevos pasan a ser **obligatorios**: editar es la ocasión natural de completarlos. `fullName` deja de actualizarse. |
| **Retiro de la columna** | Cuando no queden filas con los campos nuevos vacíos. Migración de limpieza aparte, verificada por conteo. |

El nombre **ya era obligatorio en el alta**, así que para pacientes nuevos los campos partidos también lo son — no se relaja nada. Para los existentes, completarlos entra en la misma precondición de D1: no bloquea su ficha, bloquea la **apertura** de su Historia Clínica. Cuando no queden registros con `fullName`, la columna se retira en una migración de limpieza.

**Consumidores que rompen** (verificado en el código — es cambio de **contrato y de datos**, no solo de formulario):

| Archivo | Qué cambia |
|---|---|
| `persistence/entity/Patient.java` | campos nuevos + migración |
| `service/patient/PatientData.java` · `PatientView.java` | entrada y salida del servicio |
| `persistence/specification/PatientSpecifications.java` | la búsqueda filtra por `fullName` |
| `presentation/dto/PatientRequest.java` · `PatientResponse.java` · `PatientSummaryResponse.java` | **contrato HTTP** |
| `presentation/dto/ClinicalHistoryResponse.java` | la filiación derivada que muestra la Historia |
| cliente orval + toda la UI de pacientes | regenerado y adaptado |
| tests que crean pacientes con nombre compuesto (p. ej. `ClinicalHistoryControllerIT`) | **fallan por diseño** |

El nombre para mostrar se **deriva** (`apellidos, nombres` en listados; `nombres apellidos` en la ficha) en vez de leerse de una columna.

> ⚠️ `User.fullName` (el **staff**, no el paciente) **no se toca**: es una identidad de sistema, no un registro civil sujeto a la norma.

### D5 — Las Historias ya selladas no se ven afectadas

Una Historia Clínica sellada antes de esta regla **permanece válida y no se le exige filiación retroactivamente**. Exigirlo obligaría a reabrir un documento sellado, que es exactamente lo que el modelo prohíbe (`ADR-0011`).

Los pacientes ya registrados sin documento **no se bloquean**: siguen existiendo, se pueden consultar y editar. Solo no se podrá **abrir** su Historia Clínica hasta completar la filiación — que es el comportamiento correcto, no una regresión.

## Consecuencias

- ✅ **Cumple la norma sin romper el flujo de recepción.** El caso del recién nacido sin DNI sobrevive: se admite, se atiende, y la filiación se completa antes de cerrar el documento legal.
- ✅ **`ADR-0005` y `ADR-0006` sobreviven íntegros.** No hay contradicción entre ADRs aceptados, solo una precisión de etapa que faltaba.
- ✅ **No revierte scenarios construidos.** Los del alta (documento opcional, el "bebé sin documento") siguen válidos tal cual. Los que cambian son los del **sellado**, que ganan ítems de checklist.
- ✅ **Reusa dos mecanismos probados**: el checklist con `missingFields` de `clinical-history` y el patrón de catálogo de `Cie10CatalogLoader`.
- ⚠️ **Un paciente puede existir con filiación incompleta.** Es deliberado y acotado: no se le puede sellar la Historia, y sin Historia sellada no se le puede tratar (`enabledForTreatment`). El gate real nunca se relaja.
- ⚠️ **Toca dos capacidades construidas**: `patients` (campos nuevos + migración) y `clinical-history` (precondición de **creación**). Sus tests de creación de Historia van a fallar por diseño.
- ✅ **No colisiona con `add-dental-chart`.** Al situar la exigencia en la **apertura** y no en el sellado, los dos changes tocan requirements **distintos** de `clinical-history` (creación vs. sellado) y pueden implementarse por separado. Situarlo en el sellado habría obligado a componer dos `MODIFIED` sobre el mismo requirement, con riesgo real de que el segundo pisara al primero.
- ⚠️ **Carga de datos nueva**: el catálogo de ubigeos INEI (~1 900 filas) hay que conseguirlo y versionarlo, como se hizo con `cie10-minsa.json`.
- ⚠️ **Partir el nombre (D4) es el cambio más caro de este ADR.** Toca la entidad, la migración, la **búsqueda** (`PatientSpecifications` filtra por `fullName`), cuatro DTOs, el contrato, el cliente generado y toda la interfaz de pacientes. A cambio, evita el problema irreversible: cuanto más tarde se parta, más registros habrá imposibles de separar con certeza.

## Alternativas descartadas

- **Documento obligatorio en el alta, sin excepciones** (lectura literal de la norma) — descartada. Derogaría `ADR-0005` entero, revertiría scenarios construidos y testeados, y no resuelve el caso real del recién nacido: obligaría a inventar un documento o a usar el del tutor, ensuciando el dato. Además confunde el **acto administrativo** (registrar a alguien) con el **documento médico-legal** (la historia clínica), que es lo que la norma regula.
- **Ubigeo como texto libre validado por formato** — ver D2.
- **Resolverlo con un `update-patients` sin ADR** — descartada. Contradice un ADR aceptado (`ADR-0005`) y toca el núcleo del dominio: no es un ajuste de implementación, es una decisión de modelo que debe quedar registrada con su porqué.
- **Partir `fullName` automáticamente al migrar** (heurística por espacios: últimas dos palabras = apellidos) — descartada. Acierta en los casos simples y falla en silencio en los demás: nombres compuestos, apellidos con partículas (*de la Cruz*, *Del Águila*), un solo apellido. Un dato incorrecto que nadie revisa es peor que un campo vacío que el sistema exige completar antes de sellar.
- **Bloquear a los pacientes ya registrados hasta completar su filiación** — descartada: convertiría una mejora de cumplimiento en una interrupción del servicio para fichas existentes, sin ganancia legal (sus historias no están selladas o ya lo están bajo la regla anterior).

## Open Questions

1. ✅ **Partir `fullName` — RESUELTO** (2026-08-03), ver **D4**: se parte en nombres · apellido paterno · apellido materno (este último opcional). Cierra la duda que `ADR-0005` arrastraba y que `vision.md §3` anotaba sin resolver.

2. **¿De dónde sale el catálogo de ubigeos?** INEI publica el listado, pero hay que elegir la fuente, la versión y cómo se actualiza cuando se crean distritos nuevos (pasa cada pocos años). Mismo problema que tuvo el catálogo CIE10, con la diferencia de que los ubigeos **sí cambian** con el tiempo.

3. **¿"Procedencia" es el domicilio actual o el lugar de donde viene el paciente?** El término admite lectura doble. `patients` ya tiene `address` (texto libre) — hay que decidir si el ubigeo de procedencia lo reemplaza, lo complementa o son cosas distintas.
