# Visión de producto — OdontoRisas

Dimensión de **negocio** (sustituible por proyecto). Define **qué** construimos y **en qué orden**. El **cómo** (stack, estándares) vive en `docs/architecture.md`, `docs/backend.md`, `docs/frontend.md`, `docs/coding-style.md` y `docs/testing.md`. El **modelo de dominio** vive en `docs/domain.md`.

---

## 1. Producto

**OdontoRisas** es un **SaaS multi-clínica** de gestión dental: varias clínicas (organizaciones) independientes comparten la instalación con sus datos **aislados**, y cada una administra sus pacientes (menores y adultos), citas, el **odontograma** (el *estado* dental: notación FDI, por superficie, denticiones permanente y temporal), los **tratamientos** que lo modifican, imágenes clínicas, cobros al paciente e inventario, con acceso controlado por rol dentro de su organización. La capa de plataforma (identidad, organización, membresía, aislamiento) se diseña **genérica y reutilizable** (ver capacidad `multi-tenancy`).

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
| 2 | `staff` | negocio | admin | el administrador gestiona **quién pertenece a su clínica y con qué rol**: invitar, activar/desactivar, cambiar rol. **Trae el modelo de roles real** (`ADMIN`/`DENTIST`/`RECEPTIONIST`) → lleva delta sobre `multi-tenancy`, que posee `Membership.role` | 1, `multi-tenancy` |
| 3 | `patients` | negocio | staff | ficha de pacientes (datos personales, contacto) | 1 |
| 4 | `appointments` | negocio | **staff** | agendamiento **interno**: la recepción agenda, reprograma y cambia el estado de las citas | 3, (2) |
| 5 | `dental-chart` | negocio | odontólogo | **odontograma**: el *estado* dental pieza a pieza y por superficie (FDI, denticiones permanente/temporal), hallazgos e histórico del estado | 3 |
| 6 | `treatments` | negocio | odontólogo | las *acciones* sobre ese estado: procedimientos (planificados/realizados), coste, notas clínicas y consentimiento | 3, 5 |
| 7 | `images` | negocio | odontólogo | subida de imágenes (radiografías/fotos) vía S3 | 3, 6 |
| 8 | `patient-billing` | negocio | staff / admin | **la clínica cobra al paciente**: cobros y facturación de tratamientos y citas | 4, 6 |
| 9 | `inventory` | negocio | staff / admin | insumos y stock de la clínica | 1 |
| 10 | `dashboard` | negocio | admin | panel con métricas (citas, ingresos, ocupación) | 2–9 |
| 11 | `organization-branding` | negocio | admin | marca visual por organización (color de marca y logo) en el app shell | `multi-tenancy` |

> **Por qué `dental-chart` y `treatments` van separadas** (aplicando §5). Juntas, su alcance necesitaba una "y" —*"tratamientos **y** odontograma"*—, que es la señal de mala granularidad que marca esta misma guía; y cargaban con cinco entidades (`Tooth`, `ToothCondition`, `Treatment`, `DentalNote`, `Consent`), muy por encima del límite de ~7–8 requirements. El corte no es por actor (ambas son del odontólogo) sino **por sustantivo**: el odontograma es el **estado** de la boca; el tratamiento es la **acción** que lo cambia.
>
> El acoplamiento no obliga a fusionarlas: el Scenario *"al completar un tratamiento se actualiza el odontograma"* vive en `treatments`, que **depende de** `dental-chart` (igual que `patient-billing` depende de `treatments` y son distintas). Y cada una **se sostiene sola**: consultar el estado dental tiene sentido sin registrar nada; registrar un procedimiento y su coste, también.
>
> ⚠️ Cuidado con **dos historiales distintos**: la evolución *del estado* de una pieza es de `dental-chart`; el histórico *de procedimientos* y su coste es de `treatments`. Un `#### Scenario:` que los mezcle es justo el olor que hay que evitar.

> **Por qué ya no existe una capacidad `users`** (aplicando §5). Cargaba con **tres cosas de actores distintos** —*"perfil **y** staff **y** selector de organización"*—, que es la señal de mala granularidad que marca esta guía. Se disuelve, y cada pieza va donde le corresponde:
>
> | Pieza | Actor | Dónde va |
> |---|---|---|
> | Gestionar **el personal** de la clínica (invitar, rol, alta/baja) | **admin**, sobre **otros** | ✅ capacidad **`staff`** (fila 2) |
> | Editar **mi perfil** (username, teléfono, país, contraseña) | **el usuario**, sobre **sí mismo** | change **`update-authentication`** — es dato de la *identidad*, no una capacidad nueva |
> | Cambiar de **organización activa** | usuario con varias membresías | change **`update-multi-tenancy`** — es re-emitir el token con otro `oid`: una operación de **sesión**, no de gestión de usuarios |
>
> El nombre es **`staff`** y no `memberships` porque §5 pide un **sustantivo de negocio**: el admin piensa *"gestiono el personal de mi clínica"*. `Membership` es la **entidad** sobre la que opera (de `multi-tenancy`), no la capacidad — igual que `treatments` opera sobre `ToothCondition`, que es de `dental-chart`.

> ⚠️ **El actor de `appointments` es SOLO el staff.** Antes decía *"staff / paciente"*, y eso era **inimplementable**: el paciente **no es usuario del sistema** (no tiene cuenta ni inicia sesión, ver `docs/domain.md`), así que **no puede agendar**. Un actor que no puede autenticarse no genera Scenarios válidos — y de los Scenarios salen los tests.
>
> Si ese actor hubiera sobrevivido, al proponer la capacidad alguien habría escrito *"#### Scenario: el paciente reserva una cita"*, y para hacerlo pasar habría que **darle cuenta al paciente**, deshaciendo una decisión de dominio y arrastrando un portal del paciente entero.
>
> 💡 Pero esa celda **escondía un deseo real**: la **reserva online**. No se pierde — se nombra como `public-booking` en §7. Y **no requiere dar cuenta al paciente**: se resuelve con un formulario **público sin login** (el visitante pide hueco, la clínica confirma), que es como funciona la mayoría. Sería una capacidad propia, con **otro actor** (visitante anónimo) y sus propios problemas (spam, verificación por correo). Nada que ver con el agendamiento **interno** de la fila 4.

> ⚠️ **`patient-billing` no es "el billing del SaaS".** En un SaaS multi-clínica conviven **dos negocios de cobro completamente distintos**, y confundirlos es un error caro:
>
> | | Quién cobra | A quién | Capacidad |
> |---|---|---|---|
> | **Cobro clínico** | la **clínica** | al **paciente** (tratamientos, citas) | **`patient-billing`** (fila 8) |
> | **Suscripción del SaaS** | **nosotros** | a la **clínica** (plan, límites, impago) | **`subscriptions`** — ⏳ **no priorizada**, ver §7 |
>
> Por eso la fila 8 lleva el prefijo: `billing` a secas era ambiguo y ya nos hizo dudar una vez. Dos negocios, dos capacidades, dos nombres que no se pisan.
>
> 💡 La plataforma **ya dejó el gancho puesto**: `Organization.status` contempla `SUSPENDED`, y el login/refresh hacen *gate* por ese estado (`multi-tenancy`). Es exactamente lo que `subscriptions` necesitaría para cortar el acceso por impago — **sin tocar `authentication`**.

> `multi-tenancy` y `api-type-contracts` son capacidades **técnicas** (no numeradas aquí); ver "Estado de capacidades" abajo.

---

## 3. Estado de capacidades (puente hacia `specs/` y `changes/`)

> Esta lista es el **puente vivo** entre la planificación y lo construido. **Actualízala en cada `/opsx:propose` y `/opsx:archive`** (ver "Ciclo de vida" abajo).

- ✅ **`project-foundation`** — construida. Spec: `openspec/specs/project-foundation/spec.md` · change archivado: `openspec/changes/archive/2026-07-03-add-project-scaffolding/`.
- ✅ **`authentication`** — construida. Spec: `openspec/specs/authentication/spec.md` · change archivado: `openspec/changes/archive/2026-07-07-add-authentication/`.
- ✅ **`api-type-contracts`** (técnica) — construida (contrato OpenAPI versionado + codegen orval). Spec: `openspec/specs/api-type-contracts/spec.md` · mismo change `add-authentication`.
  - ✅ **Reforzada por el change `update-api-type-contracts`** (archivado: `openspec/changes/archive/2026-07-13-update-api-type-contracts/`; la spec pasó de 3 a **7 requirements**). El contrato incumplía el propósito de su propia capacidad: documentaba `200` donde la API devuelve `201`/`204`, **no documentaba ningún error** (así que el front escribía su forma a mano), el **`401` de sesión no era RFC 9457**, y —lo más grave— **nadie vigilaba que el contrato reflejara el backend**, dejando incumplido un Scenario que la capacidad ya prometía. Ahora: **el contrato dice la verdad** (status reales + errores tipados con `errors[]` por campo), **toda la API falla con un único formato**, los errores transversales (`401`/`403`/`500`) **se inyectan solos** —las capacidades futuras los heredan sin escribir una línea— y **CI vigila las dos direcciones del drift** (`ContractDriftIT`: backend→contrato · `codegen-drift`: contrato→cliente). Convenciones en `docs/backend.md §10.1-10.2` y `docs/frontend.md §2.1`.
- ✅ **`multi-tenancy`** (técnica, **capa de plataforma reutilizable**) — **construida**. SaaS multi-tenant con modelo identidad/organización/membresía: `User` es identidad global, `Organization` es el tenant (una clínica = una organización), `Membership` (con el rol) une ambos. Aislamiento RLS de dos claves (identidad sobre `memberships`, organización sobre el negocio) + dos roles de BD (menor privilegio), contexto de organización por petición y alta autoservicio. Spec: `openspec/specs/multi-tenancy/spec.md` · change archivado: `openspec/changes/archive/2026-07-09-add-multi-tenancy/`. Modificó también `authentication` (el registro crea organización + identidad + membresía ADMIN; el rol pasó de `User` a la membresía; el token identifica la organización activa; gate por estado en login/refresh).
- ✅ **`organization-branding`** (negocio) — **construida**. Personalización visual por organización (color de marca y logo por URL) aplicada al **app shell** post-login; tema derivado del color de marca con foregrounds por contraste (WCAG AA), respeta dark mode y no altera los colores semánticos; edición solo `ADMIN`; logo por URL `https` con respaldo al logo del producto; el favicon sigue siendo el del producto. Spec: `openspec/specs/organization-branding/spec.md` · change archivado: `openspec/changes/archive/2026-07-11-add-organization-branding/`. Depende de `multi-tenancy` (✅).
- ⏳ **Resto** (`staff`, `patients`, `dental-chart`, `treatments`, `appointments`, `patient-billing`, `images`, `inventory`, `dashboard`) — planeadas. *(`dental-chart` es el **odontograma** —el estado dental— y `treatments` las **acciones** que lo modifican; `staff` es la gestión del personal por el admin. Los tres se recortaron **antes** de construirlos, ver §2.)*

- 🚧 **`multi-tenancy`** — **change en progreso: `update-multi-tenancy`** (el **cimiento**). Cierra el aislamiento que quedó a medias: el contexto de organización pasa a fijarse **solo** en cada transacción, de modo que **una capacidad de negocio nueva queda aislada sin escribir código de aislamiento** (*por defecto, no por disciplina* — un olvido aquí no falla: **filtra historia clínica entre clínicas**). Cierra además una **promesa incumplida**: su spec ya exigía que *"toda consulta de negocio quede restringida a la organización"*, pero **nunca fue verificable** porque no había ni una tabla de negocio. Change: `openspec/changes/update-multi-tenancy/proposal.md` y `design.md`.
- 🚧 **`patients`** (negocio) — **en progreso**, **depende de `update-multi-tenancy`**. La ficha del paciente: el **núcleo del dominio** (de ella cuelgan `dental-chart`, `treatments`, `appointments`, `images`, `patient-billing`) y la **primera capacidad de negocio** — la primera prueba real de la garantía de aislamiento. **No escribe ni una línea de código de aislamiento**: declara su entidad siguiendo la convención y el aislamiento ya está. Change: `openspec/changes/add-patients/proposal.md` y `design.md`.

**Pendientes sobre capacidades ya construidas** (salen de disolver `users`, ver §2 — son `update-*`, no capacidades nuevas):

- 📌 **`update-authentication`** — el **perfil del usuario**: `username` (único), `phone`, `country` y cambio de contraseña. Es dato de la **identidad**, así que amplía `authentication` en vez de inventar una capacidad. *(Estos campos ya estuvieron y se retiraron del registro en `add-multi-tenancy`; aquí vuelven, pero al perfil, no al alta.)*
- 📌 **Modelo de roles** — hoy solo hay `ADMIN` | `USER`, así que **la recepcionista y el odontólogo compartirían rol** y la recepcionista podría tocar el odontograma (un **acto clínico**). Se desdobla en `ADMIN` | `DENTIST` | `RECEPTIONIST`. **No es un change aparte**: lo lleva `add-staff` como delta sobre `multi-tenancy`, porque `staff` *es* asignar roles y no se puede especificar sin decidirlos. Ver la tabla de actores en `docs/domain.md`.
- 📌 **`update-multi-tenancy`** — el **selector de organización activa**: un usuario con varias membresías cambia de clínica, lo que **re-emite el token con otro `oid`**. Es una operación de **sesión**, no de gestión de usuarios.

> ⛔ **Bloqueo de secuencia:** ninguna capacidad de negocio (`staff`, `patients`, …) debe proponerse antes de archivar `multi-tenancy` (✅ ya archivada). Si una entidad de negocio nace sin organización, el error se arrastra a `appointments`, `treatments`, `images` y `patient-billing`.

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
- **`patients` es el núcleo del dominio**: `appointments`, `dental-chart`, `treatments` e `images` cuelgan de él.
- **`dental-chart` antes que `treatments`**: el tratamiento *modifica* el estado dental, así que el estado debe existir primero.
- **`dashboard` al final**: agrega datos de casi todas las capacidades; construirlo antes obligaría a rehacerlo.
- `inventory` es relativamente independiente; puede adelantarse si el negocio lo prioriza.

## 7. Fuera de alcance por ahora (candidatas futuras)

- **`subscriptions`** — **el negocio del SaaS: cobrarle a la clínica.** Plan contratado, límites del plan, ciclo de facturación, impago y suspensión. **No confundir con `patient-billing`** (fila 8), que es la clínica cobrando *al paciente*: son dos negocios distintos (ver el aviso en §2).
  > Se nombra aquí **aunque no esté priorizada** porque un SaaS sin forma de cobrar su suscripción es un hueco que conviene **ver desde el principio**, no descubrir tarde. La plataforma ya dejó el gancho: `Organization.status` contempla `SUSPENDED` y el login/refresh hacen *gate* por él — cortar el acceso por impago **no requeriría tocar `authentication`**.
- **`public-booking`** — **la reserva online**: un visitante (sin cuenta) solicita hueco desde la web y la clínica confirma. Actor: **visitante anónimo**, distinto del staff de `appointments` (fila 4, agendamiento *interno*).
  > **No requiere dar cuenta al paciente** —que sigue sin ser usuario del sistema—: se resuelve con un formulario **público sin login**. Trae problemas propios (endpoint público → spam y abuso, verificación por correo, huecos disponibles sin filtrar datos de otros pacientes), por eso es capacidad aparte y no un Scenario más de `appointments`.
- `chatbot` (Spring AI ya está en el stack "para el bot") — no priorizada aún.
- `notifications` (correo/realtime STOMP) — se evaluará cuando `appointments` lo requiera.

**Tooling de plataforma (transversal, aún NO adoptado):**
- **Seguridad (Snyk)**: escaneo de vulnerabilidades. Escalonado: primero **Dependabot** + `pnpm audit` (front) + **OWASP Dependency-Check** (Maven, back) en CI —sin cuentas externas—; **Snyk** después para SAST/licencias/contenedores (requiere cuenta + `SNYK_TOKEN`).
- **Observabilidad (Sentry)**: monitoreo de errores + performance en front (React) y back (Spring Boot). Se apoya en el `traceId` (RFC 9457 + MDC) ya existente. Se monta con el setup de deploy/entornos.

> El orden no es rígido: se ajusta según prioridad de negocio. Cambiar el orden no cambia las reglas de granularidad de este documento.
