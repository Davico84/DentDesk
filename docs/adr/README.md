# ADRs — cómo se escriben y se revisan

Un **ADR** registra una **decisión y su porqué**, con las alternativas que se descartaron. No registra *qué construir*: eso vive en el change de OpenSpec (`proposal.md` / `design.md` / `spec.md` / `tasks.md`).

| | ADR | Change de OpenSpec |
|---|---|---|
| Responde | **por qué** se decidió así | **qué** construir y **cómo** |
| Vive | `docs/adr/NNNN-*.md` | `openspec/changes/<id>/` |
| Cambia | casi nunca (se supersede, no se edita) | en cada iteración hasta archivar |

**Cuándo escribir uno:** cuando la decisión sobrevive al change que la implementa, cruza capacidades, toca algo ya construido, o descarta alternativas que alguien podría re-proponer dentro de seis meses sin ver los argumentos.

Ejemplos de referencia por densidad y formato: [`0011`](0011-historia-clinica-documento-inicial.md) y [`0012`](0012-odontograma-libro-de-asientos.md).

---

## Las cinco dimensiones

Aplican **al escribir** un ADR, no solo al revisarlo. La mayoría de los huecos que aparecen en revisión son de una dimensión que no se miró al redactar.

### 1. Cumplimiento normativo

MINSA, COP, protección de datos. ¿Qué norma **exige** esto, y qué dice exactamente el fragmento que lo exige?

- Citar el fragmento puntual, no un resumen de la norma completa (un resumen local se desactualiza en silencio).
- Registrar la fila correspondiente en [`docs/compliance/regulatory-matrix.md`](../compliance/regulatory-matrix.md).
- Si la decisión **no** cumple del todo y se difiere, decirlo explícitamente — un hueco documentado es gestionable; uno implícito, no.

### 2. Dominio clínico

¿Representa la práctica odontológica real, no una idealización?

- ¿Un odontólogo reconocería el modelo y el vocabulario?
- Los casos raros pero reales (temporal retenida en un adulto, teléfono compartido en familia) **no se bloquean**: se avisan. Bloquearlos impide registrar la realidad.
- ¿La restricción propuesta impediría registrar un caso legítimo?

### 3. DDD

- **Aggregate Root**: ¿cuál es y por qué? ¿Qué contiene exactamente?
- **Invariantes numeradas** — la ausencia de esta lista es el hueco más frecuente. Distinguir **duras** (el aggregate rechaza) de **blandas** (avisa).
- **Value Objects** y enumeraciones: ¿qué es identidad y qué es valor?
- **Límites**: ¿qué **NO** pertenece al aggregate, y a qué capacidad va?
- Si dos cosas deben cambiar y sellarse siempre juntas, o están en el mismo aggregate, o el invariante se protege explícitamente en un caso de uso (y se dice que es una desviación consciente).

### 4. Arquitectura

Consistencia con lo que ya existe. Comprobar **contra el código, no de memoria**:

- ¿Hay una regla ArchUnit que lo bloquee? (`@Async`/`@Scheduled` lo están, hasta cablear el contexto de tenant.)
- ¿Respeta la dirección de dependencia declarada en `docs/vision.md §2`?
- ¿Reusa lo construido, o redefine algo que ya existe? (`FdiTooth`, `ProblemDetails`, el patrón de sellado de `clinical-history`…)
- ¿Introduce infraestructura nueva? Si sí: ¿qué garantía da que no dé la alternativa sin infraestructura?
- Aislamiento por organización: toda entidad de negocio lleva el suyo — **la RLS de Postgres no es transitiva**, no se hereda por FK.

### 5. Evolución futura

- ¿Soporta los módulos que vienen sin refactor grande?
- ¿Se puede exponer ya la señal que un consumidor futuro necesitará, para no rehacer el contrato después? (precedente: `enabledForTreatment`, construido antes que `treatments`).
- ¿Qué se difiere, y qué lo desbloquea?
- ¿El alcance cabe en una capacidad, o hay que partirlo? (`docs/vision.md §5`: > ~7-8 requirements es señal de partir.)

---

## Estructura

```markdown
# ADR-NNNN: <decisión en una frase>

- **Estado:** Propuesto | Aceptado | Supersedido por ADR-NNNN
- **Fecha:** YYYY-MM-DD
- **Decisores:**

## Contexto          ¿qué problema, con qué restricciones reales?
## Decisión          numeradas (D1, D2…) si son varias
## Modelo resultante (si la decisión define un modelo de dominio)
## Consecuencias     ✅ lo que gana · ⚠️ lo que cuesta o queda pendiente
## Alternativas descartadas   qué se consideró y POR QUÉ no
```

**"Alternativas descartadas" no es relleno.** Es la sección que evita re-litigar la misma discusión en seis meses. Incluir también las propuestas que se rechazaron **en revisión**, con el argumento.

**Un ADR no se edita para cambiar de opinión**: se escribe uno nuevo que lo supersede, y el viejo queda con su estado actualizado. Sí se corrige por error o para agregar detalle que faltaba.
