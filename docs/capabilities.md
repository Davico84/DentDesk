# Capacidades (capabilities) — convención de granularidad

Cómo definir y partir las capacidades de OpenSpec en OdontoRisas. Complementa `SETUP-OPENSPEC.md` (flujo) y `docs/architecture.md` (arquitectura).

---

## 1. Qué es una capacidad

Una **capacidad** es un área cohesiva de comportamiento del sistema, con su propio `openspec/specs/<nombre>/spec.md`. Dos tipos:

| Tipo | Qué es | Ejemplos | Forma de sus Scenarios |
|---|---|---|---|
| **De negocio** | lo que el usuario final puede hacer | `authentication`, `appointments`, `patients` | flujos de usuario (WHEN el usuario hace X → THEN…) |
| **Técnica / plataforma** | base sobre la que se construye el negocio | `project-foundation` | comportamientos de sistema/dev (WHEN se ejecuta `pnpm validate` → THEN…) |

`specs/` contiene solo lo **ya construido**. Cada capacidad nace como *change* (`add-<capacidad>`) y llega a `specs/` al archivar.

---

## 2. Regla de granularidad

**Una capacidad = un sustantivo, un actor/objetivo principal, un conjunto cohesivo de requirements.**

**Mantén junto** cuando:
- Los requirements comparten el mismo actor y ciclo de vida (p. ej. todo lo relativo a identidad/sesión).
- Partirlo obligaría a que un fragmento no tenga sentido por sí solo.

**Parte en varias capacidades** cuando:
- Aparecen **actores u objetivos distintos** (paciente vs. odontólogo vs. admin) con flujos independientes.
- El `spec.md` crece demasiado (guía: **> ~7–8 requirements** o cubre temas no relacionados).
- Dos áreas evolucionan y se despliegan por separado.

> Señal de mala granularidad: si un `#### Scenario:` mezcla dos objetivos no relacionados, o si el nombre de la capacidad necesita un "y" (`login-y-pagos`), hay que partir.

---

## 3. Nomenclatura

- Nombre de capacidad: **kebab-case, sustantivo** (`authentication`, `appointments`), no verbo.
- `change-id`: **verbo + capacidad** en kebab-case → `add-authentication`, `update-appointments`, `remove-...`.
- Un *change* modifica **una** capacidad siempre que sea posible; si toca varias, crea un `spec.md` delta por cada una.

---

## 4. Mapeo capacidad ↔ código

Cada capacidad de negocio se materializa de forma consistente en ambos módulos:

| Capa | Ubicación por capacidad `<cap>` |
|---|---|
| Backend — presentación | `presentation/controller/<Cap>Controller`, `presentation/dto/...` |
| Backend — lógica | `infra/service/<cap>/...` (servicio por dominio) |
| Backend — datos | `persistence/entity`, `persistence/repository`, `persistence/specification` |
| Frontend — módulo | `frontend/src/modules/<cap>/` (services, components, hooks) |
| Frontend — pantallas | `frontend/src/screens/<área>/...` |

Lo transversal (auth guard, roles) vive en `frontend/src/modules/core/` y en `infra/security` (backend).

---

## 5. Decisiones tomadas

- **`authentication` es UNA sola capacidad** (no se parte en `registration` / `session` / `authorization`).
  - *Razón*: todos sus requirements giran en torno a un mismo dominio cohesivo (identidad, sesión y acceso), comparten el actor "usuario" y el mismo modelo de token/rol. Partirlo generaría specs con fragmentos que no se sostienen solos.
  - Cubre: registro, login, persistencia de sesión, refresh de token, logout y protección de rutas por auth y rol (ver `SETUP-OPENSPEC.md §4`).
- La autorización por rol (RouteGuard / seguridad por rol) es **parte de** `authentication`, no una capacidad aparte.

---

## 6. Checklist al proponer una capacidad

- [ ] ¿Tiene un sustantivo claro y un actor/objetivo principal?
- [ ] ¿Sus requirements son cohesivos (no mezcla dominios no relacionados)?
- [ ] ¿≤ ~7–8 requirements? Si no, ¿procede partir?
- [ ] ¿El `change-id` es `verbo-capacidad` en kebab-case?
- [ ] ¿Está claro su mapeo a paquetes del backend y módulo/pantallas del frontend?
