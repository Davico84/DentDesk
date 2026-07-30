## Why

El **odontograma** es el registro gráfico del estado dental pieza a pieza, y por norma peruana (`NTS N°139-MINSA`) es un **anexo obligatorio de la historia clínica**: sin él, la Historia Clínica que ya construimos está incompleta como documento médico-legal. Hoy `dental-chart` solo existe como un stub en `docs/domain.md` — y ese stub quedó **invalidado** por el modelado a fondo del dominio (`ADR-0012`), que encontró que su `status` mezclaba estado con acción y que no podía localizar dos condiciones que la norma exige registrar.

Además es el **prerrequisito estructural** de las capacidades clínicas que siguen: `treatments` (fila 7) modifica el estado dental, y sin ese estado no hay nada que modificar.

## What Changes

- **Nueva capacidad `dental-chart`**, acotada al **odontograma inicial** (`ADR-0012` D9). La evolución diaria queda fuera (ver Non-goals).
- El odontograma se modela como un **bloque fechado** con hallazgos, no como un estado mutable. Cerrar un hallazgo agrega un asiento nuevo; nunca actualiza el anterior (`ADR-0012` D3/D4).
- **Localización en tres formas**: superficie de una pieza, pieza completa, y **entre dos piezas** — esta última necesaria para diastema y supernumerario, que no tienen número FDI propio (`ADR-0012` D8).
- **Catálogo cerrado de tipos de condición**, cada uno con su naturaleza clínica (`REQUIERE_INTERVENCIÓN` / `SIN_PATOLOGÍA`, que determina el color rojo/azul) y su alcance (`ADR-0012` D2/D8).
- **BREAKING** — **el sellado de la Historia Clínica deja de ser una operación autónoma.** Por norma, el odontograma inicial y la admisión no pueden modificarse de forma independiente, así que ambos se sellan en **una sola transacción** coordinada (`ADR-0012` D5). La `HistoriaClinica` ya no pasa a `COMPLETED` por sí sola.
- **Se invalida el stub** `Tooth` + `ToothCondition` de `docs/domain.md`: el `status` (`EXISTING`/`PLANNED`/`DONE`) sale del modelo por mezclar estado con acción, la FK `→ Treatment` desaparece por invertir la dependencia declarada, y `Tooth` como tabla es redundante (ya existe como código: `FdiTooth`).

## Capabilities

### New Capabilities
- `dental-chart`: el **estado** dental pieza a pieza y por superficie — el odontograma inicial como documento sellado, con hallazgos localizados y tipificados. No incluye las **acciones** que lo cambian (eso es `treatments`).

### Modified Capabilities
- `clinical-history`: el requirement de sellado (`Sellado a COMPLETED con checklist mínimo afirmativo`) pasa a exigir que exista un odontograma inicial y a **sellarse atómicamente con él**. Es un cambio de comportamiento a nivel spec, no de implementación: hoy la Historia se sella sola y después de este change ya no.

## Non-goals

Alcance **excluido a propósito** de este change:

- **Odontograma de evolución** (bloques diarios, asientos de apertura/cierre, cronología lineal forzada, proyección del estado a una fecha). Va a `update-dental-chart`, **junto con** la nota SOAP de `update-clinical-history`: ambas sellan en el mismo "Cerrar Cita" y construirlas por separado obligaría a montar la coordinación dos veces (`ADR-0012` D9).
- **Comparación del estado entre dos fechas.** Depende de la evolución; sin bloques posteriores no hay nada que comparar. La evidencia de "cómo llegó el paciente" sí queda cubierta: es el odontograma inicial sellado.
- **Cierre automático a fin de día.** `@Scheduled` está prohibido por la regla ArchUnit `async_must_not_be_used_until_tenant_context_is_propagated` hasta cablear el `TenantContextTaskDecorator` (`ADR-0012` D6).
- **Integración con `treatments`** (el asiento automático al completar un tratamiento). Esa capacidad no existe todavía; su operación se diseña en la fase 2, cuando haya un consumidor real que la dispare.
- **Retención legal de 15 años y archivo pasivo.** Requisito normativo real, **sin ADR ni change** — el mayor hueco abierto de `docs/compliance/regulatory-matrix.md`. Un libro append-only garantiza que nada se sobrescriba mientras exista, pero no es una política de conservación.
- **Vínculo del profesional con su colegiatura COP.** La autoría se registra por `userId`; el vínculo formal llega con `staff`.
- **Remover la anamnesis de `patients`.** Pendiente independiente (`ADR-0011`), no se toca acá.

## Impact

**Backend**
- Migraciones Flyway nuevas (la última en `dev` es `V15__clinical_history_diagnosis.sql`): tabla del bloque de odontograma y tabla de hallazgos, cada una con `organization_id` + `ENABLE`/`FORCE ROW LEVEL SECURITY` y su policy — **la RLS no se hereda por FK**, hallazgo que ya costó una auditoría en `add-clinical-history`.
- Índices únicos **parciales** para las invariantes de unicidad: un chequeo previo en el servicio pierde ante solicitudes concurrentes (precedente pagado con `UNIQUE(patient_id)` en `add-clinical-history`).
- Entidades JPA con `@TenantId` + autoría (`@CreatedBy`/`@LastModifiedBy`), exigidas por el candado ArchUnit `business_entities_with_tenant_id_must_declare_authorship`.
- **Caso de uso coordinador** por encima de `clinical-history` y `dental-chart`, transaccional. Ninguno de los dos módulos depende del otro; el orquestador depende de ambos.
- Reusa `FdiTooth` (conjunto real de piezas, ya construido) — no lo redefine.

**Contrato y frontend**
- Endpoints nuevos del odontograma; `contracts/openapi.json` regenerado + cliente orval (commit aparte, `ContractDriftIT` vigila el drift).
- Módulo `modules/frontend/src/modules/dental-chart/` con la representación gráfica y el código de color derivado de la naturaleza del tipo (**nunca colores hardcodeados**: el tema se deriva de la marca de cada clínica).

**Documentación**
- `docs/domain.md`: el stub de `dental-chart` se reemplaza por el modelo real al archivar.
- `docs/compliance/regulatory-matrix.md`: las filas del odontograma pasan de `pendiente` al requirement/scenario concreto.

**Riesgo principal**
El cambio en el sellado de `clinical-history` **toca una capacidad construida, archivada y testeada**. Sus tests de sellado existentes van a fallar por diseño y hay que actualizarlos con el nuevo contrato — no es un efecto colateral inesperado sino el trabajo previsto.
