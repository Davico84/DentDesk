# Roadmap de capacidades — OdontoRisas

Producto: **clínica dental**. Este documento lista las capacidades **planeadas** y su orden sugerido.

> ⚠️ No es `openspec/specs/`. `specs/` contiene solo lo **ya construido**; este roadmap es una ayuda de planificación. Cada capacidad nace como *change* (`add-<capacidad>`) y llega a `specs/` al archivar. Ver granularidad en `docs/capabilities.md`.

---

## Orden sugerido

| # | Capacidad | Tipo | Actor principal | Alcance breve | Depende de |
|---|---|---|---|---|---|
| 0 | `project-foundation` | técnica | dev | esqueleto ejecutable/validable del monorepo | — |
| 1 | `authentication` | negocio | usuario | registro, login, sesión, refresh, logout, protección por rol | 0 |
| 2 | `users` | negocio | admin | gestión de usuarios/staff (roles, alta/baja) | 1 |
| 3 | `patients` | negocio | staff | ficha de pacientes (datos personales, contacto) | 1 |
| 4 | `appointments` | negocio | staff / paciente | agendamiento y estado de citas | 3, (2) |
| 5 | `treatments` | negocio | odontólogo | tratamientos, historia clínica / odontograma | 3 |
| 6 | `images` | negocio | odontólogo | subida de imágenes (radiografías/fotos) vía S3 | 3, 5 |
| 7 | `billing` | negocio | staff / admin | cobros y facturación de tratamientos/citas | 4, 5 |
| 8 | `inventory` | negocio | staff / admin | insumos y stock de la clínica | 1 |
| 9 | `dashboard` | negocio | admin | panel con métricas (citas, ingresos, ocupación) | 2–8 |

---

## Estado de capacidades (puente hacia `specs/` y `changes/`)

> Esta lista es el **puente vivo** entre la planificación y lo construido. **Actualízala en cada `/opsx:propose` y `/opsx:archive`** (ver "Ciclo de vida" abajo).

- ✅ **`project-foundation`** — construida. Spec: `openspec/specs/project-foundation/spec.md` · change archivado: `openspec/changes/archive/2026-07-03-add-project-scaffolding/`.
- 🚧 **`authentication`** — en progreso. Change `add-authentication`: `openspec/changes/add-authentication/proposal.md` · `design.md`. Propuesto (pendiente de `/opsx:apply`).
- 🚧 **`api-type-contracts`** (técnica) — en progreso dentro del mismo change `add-authentication` (contrato OpenAPI versionado + codegen orval).
- ⏳ **Resto** (`users`, `patients`, `appointments`, `treatments`, `images`, `billing`, `inventory`, `dashboard`) — planeadas.

---

## Ciclo de vida de una capacidad (gobernanza)

```
planeada (este roadmap) → en progreso (change activo: proposal.md + design.md) → construida (specs/<cap>/spec.md; change en changes/archive/)
```

Disciplina para que el contexto de la IA se mantenga preciso (su contexto se extrae de `specs/` y `docs/`):

- **En `/opsx:propose`**: enlaza el change nuevo (`openspec/changes/<id>/proposal.md` y `design.md`) en "Estado de capacidades" y márcala 🔜/en progreso.
- **En `/opsx:archive`** (en el **mismo commit/PR** que el archivado):
  1. Si la capacidad tocó el dominio, **actualiza `docs/data-model.md`** — es la fuente de la que la IA extrae el modelo; desincronizarla degrada la precisión.
  2. Marca la capacidad como ✅ **construida** y enlaza su `spec.md` en `openspec/specs/`.
  3. Verifica que el change quedó en `openspec/changes/archive/`.

Regla equivalente para agentes en `CLAUDE.md → Flujo de trabajo → Gobernanza del ciclo`.

---

## Notas de secuencia

- **`authentication` primero**: todo lo demás asume identidad + roles.
- **`patients` es el núcleo del dominio**: `appointments`, `treatments` e `images` cuelgan de él.
- **`dashboard` al final**: agrega datos de casi todas las capacidades; construirlo antes obligaría a rehacerlo.
- `inventory` es relativamente independiente; puede adelantarse si el negocio lo prioriza.

## Fuera de alcance por ahora (candidatas futuras)

- `chatbot` (Spring AI ya está en el stack "para el bot") — no priorizada aún.
- `notifications` (correo/realtime STOMP) — se evaluará cuando `appointments` lo requiera.

> El orden no es rígido: se ajusta según prioridad de negocio. Cambiar el orden no cambia las reglas de granularidad de `docs/capabilities.md`.
