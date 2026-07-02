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

**Estado actual:** `project-foundation` propuesta (change `add-project-scaffolding`, pendiente de aplicar). El resto: planeadas.

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
