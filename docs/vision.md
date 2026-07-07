# Visión de producto — OdontoRisas

Dimensión de **negocio** (sustituible por proyecto). Define **qué** construimos y **en qué orden**. El **cómo** (stack, estándares) vive en `docs/architecture.md`, `docs/backend.md`, `docs/frontend.md`, `docs/coding-style.md` y `docs/testing.md`. El **modelo de dominio** vive en `docs/domain.md`.

---

## 1. Producto

**OdontoRisas** es el sistema de gestión de una **clínica dental**: administra pacientes (menores y adultos), citas, tratamientos con **odontograma** (notación FDI, por superficie, denticiones permanente y temporal), imágenes clínicas, facturación e inventario, con acceso controlado por rol.

**Objetivos:**
- Centralizar la **ficha dental** del paciente (datos clínicos/anamnesis, odontograma, tratamientos, notas, pagos, imágenes e historial de citas) en una vista única.
- Soportar el flujo real de la clínica: agendar → atender → registrar tratamiento sobre la pieza/superficie → cobrar.
- Seguridad y trazabilidad: identidad + rol en toda operación; consentimiento informado (con tutor para menores).

---

## 2. Roadmap de capacidades

> ⚠️ No es `openspec/specs/`. `specs/` contiene solo lo **ya construido**; este roadmap es ayuda de planificación. Cada capacidad nace como *change* (`add-<capacidad>`) y llega a `specs/` al archivar.

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

## 3. Estado de capacidades (puente hacia `specs/` y `changes/`)

> Esta lista es el **puente vivo** entre la planificación y lo construido. **Actualízala en cada `/opsx:propose` y `/opsx:archive`** (ver "Ciclo de vida" abajo).

- ✅ **`project-foundation`** — construida. Spec: `openspec/specs/project-foundation/spec.md` · change archivado: `openspec/changes/archive/2026-07-03-add-project-scaffolding/`.
- ✅ **`authentication`** — construida. Spec: `openspec/specs/authentication/spec.md` · change archivado: `openspec/changes/archive/2026-07-07-add-authentication/`.
- ✅ **`api-type-contracts`** (técnica) — construida (contrato OpenAPI versionado + codegen orval). Spec: `openspec/specs/api-type-contracts/spec.md` · mismo change `add-authentication`.
- ⏳ **Resto** (`users`, `patients`, `appointments`, `treatments`, `images`, `billing`, `inventory`, `dashboard`) — planeadas. *(La capacidad `users` re-añadirá al registro/perfil: nombre de usuario, teléfono, país.)*

---

## 4. Ciclo de vida de una capacidad (gobernanza)

```
planeada (este roadmap) → en progreso (change activo: proposal.md + design.md) → construida (specs/<cap>/spec.md; change en changes/archive/)
```

Disciplina para que el contexto de la IA se mantenga preciso (su contexto se extrae de `specs/` y `docs/`):

- **En `/opsx:propose`**: enlaza el change nuevo (`openspec/changes/<id>/proposal.md` y `design.md`) en "Estado de capacidades" y márcala 🚧/en progreso.
- **En `/opsx:archive`** (en el **mismo commit/PR** que el archivado):
  1. Si la capacidad tocó el dominio, **actualiza `docs/domain.md`** — es la fuente de la que la IA extrae el modelo; desincronizarla degrada la precisión.
  2. Marca la capacidad como ✅ **construida** y enlaza su `spec.md` en `openspec/specs/`.
  3. Verifica que el change quedó en `openspec/changes/archive/`.

Regla equivalente para agentes en `CLAUDE.md → Flujo de trabajo → Gobernanza del ciclo`.

---

## 5. Convención de granularidad de capacidades

**Una capacidad = un sustantivo, un actor/objetivo principal, un conjunto cohesivo de requirements**, con su propio `openspec/specs/<nombre>/spec.md`. Dos tipos: **de negocio** (lo que el usuario final hace: `authentication`, `appointments`) y **técnica/plataforma** (base sobre la que se construye: `project-foundation`, `api-type-contracts`).

**Mantén junto** cuando los requirements comparten actor y ciclo de vida, o cuando partirlo dejaría fragmentos sin sentido propio. **Parte** cuando aparecen actores/objetivos distintos con flujos independientes, cuando el `spec.md` crece demasiado (guía: **> ~7–8 requirements**), o cuando dos áreas evolucionan/despliegan por separado.

> Señal de mala granularidad: un `#### Scenario:` que mezcla dos objetivos no relacionados, o un nombre de capacidad que necesita "y" (`login-y-pagos`).

**Nomenclatura**: capacidad en **kebab-case sustantivo** (`authentication`); `change-id` = **verbo + capacidad** (`add-authentication`, `update-appointments`). Un change modifica **una** capacidad siempre que sea posible; si toca varias, crea un `spec.md` delta por cada una.

**Decisión tomada**: `authentication` es **UNA** sola capacidad (no se parte en registration/session/authorization); la autorización por rol es **parte de** ella. Razón: todos sus requirements giran en torno a identidad, sesión y acceso, con el mismo actor y modelo de token/rol.

---

## 6. Notas de secuencia

- **`authentication` primero**: todo lo demás asume identidad + roles.
- **`patients` es el núcleo del dominio**: `appointments`, `treatments` e `images` cuelgan de él.
- **`dashboard` al final**: agrega datos de casi todas las capacidades; construirlo antes obligaría a rehacerlo.
- `inventory` es relativamente independiente; puede adelantarse si el negocio lo prioriza.

## 7. Fuera de alcance por ahora (candidatas futuras)

- `chatbot` (Spring AI ya está en el stack "para el bot") — no priorizada aún.
- `notifications` (correo/realtime STOMP) — se evaluará cuando `appointments` lo requiera.

**Tooling de plataforma (transversal, aún NO adoptado):**
- **Seguridad (Snyk)**: escaneo de vulnerabilidades. Escalonado: primero **Dependabot** + `pnpm audit` (front) + **OWASP Dependency-Check** (Maven, back) en CI —sin cuentas externas—; **Snyk** después para SAST/licencias/contenedores (requiere cuenta + `SNYK_TOKEN`).
- **Observabilidad (Sentry)**: monitoreo de errores + performance en front (React) y back (Spring Boot). Se apoya en el `traceId` (RFC 9457 + MDC) ya existente. Se monta con el setup de deploy/entornos.

> El orden no es rígido: se ajusta según prioridad de negocio. Cambiar el orden no cambia las reglas de granularidad de este documento.
