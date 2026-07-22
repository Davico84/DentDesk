# Visión de producto — Dentdesk

Dimensión de **negocio** (sustituible por proyecto). Define **qué** construimos y **en qué orden**. El **cómo** (stack, estándares) vive en `docs/architecture.md`, `docs/backend.md`, `docs/frontend.md`, `docs/coding-style.md` y `docs/testing.md`. El **modelo de dominio** vive en `docs/domain.md`.

> **Nombre del producto: Dentdesk** (decisión 2026-07-20). El repo, el código (`com.odontorisas`,
> roles de BD) y la documentación de ingeniería conservan el nombre interno **OdontoRisas** por
> estabilidad — no cambia lo que renderiza el producto. Ver `CLAUDE.md`.

---

## 1. Producto

**Dentdesk** es un **SaaS multi-clínica** de gestión dental: varias clínicas (organizaciones) independientes comparten la instalación con sus datos **aislados**, y cada una administra sus pacientes (menores y adultos), citas, el **odontograma** (el *estado* dental: notación FDI, por superficie, denticiones permanente y temporal), los **tratamientos** que lo modifican, imágenes clínicas, cobros al paciente e inventario, con acceso controlado por rol dentro de su organización. La capa de plataforma (identidad, organización, membresía, aislamiento) se diseña **genérica y reutilizable** (ver capacidad `multi-tenancy`).

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
| 7 | `treatment-plans` | negocio | odontólogo | planes de tratamiento del paciente: crear el plan, agregar tratamientos, generar presupuesto y finalizarlo | 3, 6 |
| 8 | `images` | negocio | odontólogo | subida de imágenes (radiografías/fotos) vía S3 | 3, 6 |
| 9 | `patient-billing` | negocio | staff / admin | **la clínica cobra al paciente**: cobros y facturación de tratamientos y citas | 4, 6 |
| 10 | `inventory` | negocio | staff / admin | insumos y stock de la clínica | 1 |
| 11 | `dashboard` | negocio | admin | panel con métricas (citas, ingresos, ocupación) | 2–10 |
| 12 | `organization-branding` | negocio | admin | marca visual por organización (color de marca y logo) en el app shell | `multi-tenancy` |

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
  - ✅ **Reforzada por el change `update-api-type-contracts`** (archivado: `openspec/changes/archive/2026-07-13-update-api-type-contracts/`; la spec pasó de 3 a **7 requirements**). El contrato incumplía el propósito de su propia capacidad: documentaba `200` donde la API devuelve `201`/`204`, **no documentaba ningún error** (así que el front escribía su forma a mano), el **`401` de sesión no era RFC 9457**, y —lo más grave— **nadie vigilaba que el contrato reflejara el backend**, dejando incumplido un Scenario que la capacidad ya prometía. Ahora: **el contrato dice la verdad** (status reales + errores tipados con `errors[]` por campo), **toda la API falla con un único formato**, los errores transversales (`401`/`403`/`500`) **se inyectan solos** —las capacidades futuras los heredan sin escribir una línea— y **CI vigila las dos direcciones del drift** (`ContractDriftIT`: backend→contrato · `codegen-drift`: contrato→cliente). Convenciones en `docs/backend.md §10.1-10.2` y `docs/frontend.md §2.6`.
- ✅ **`multi-tenancy`** (técnica, **capa de plataforma reutilizable**) — **construida**. SaaS multi-tenant con modelo identidad/organización/membresía: `User` es identidad global, `Organization` es el tenant (una clínica = una organización), `Membership` (con el rol) une ambos. Aislamiento RLS de dos claves (identidad sobre `memberships`, organización sobre el negocio) + dos roles de BD (menor privilegio), contexto de organización por petición y alta autoservicio. Spec: `openspec/specs/multi-tenancy/spec.md` · change archivado: `openspec/changes/archive/2026-07-09-add-multi-tenancy/`. Modificó también `authentication` (el registro crea organización + identidad + membresía ADMIN; el rol pasó de `User` a la membresía; el token identifica la organización activa; gate por estado en login/refresh).
  - ✅ **Cerrada por el change `update-multi-tenancy`** (archivado: `openspec/changes/archive/2026-07-15-update-multi-tenancy/`; la spec ganó 1 requirement). El aislamiento quedaba **a medias a propósito**: el contexto se fijaba a mano solo en los flujos de auth, y su spec ya prometía que *"toda consulta de negocio queda restringida a la organización"* — **promesa inverificable** porque no había ni una tabla de negocio. Ahora el contexto se fija **solo** en cada transacción (`TenantAwareTransactionManager`), de modo que **una capacidad de negocio nueva queda aislada sin escribir código de aislamiento** (*por defecto, no por disciplina* — un olvido aquí no falla: **filtra historia clínica entre clínicas**). Y no puede degradarse: una regla de ArchUnit **rompe la build** si el código de negocio intenta fijar el contexto a mano. Verificado con una tabla de prueba como rol no-superusuario (`AutomaticTenantBindingIT`).
  - ✅ **Extendida por el change `update-multi-tenancy` (autoría)** (archivado: `openspec/changes/archive/2026-07-19-update-multi-tenancy/`; la spec ganó 1 requirement más). Mismo mecanismo automático que ya fija el tenant, ahora también para **autoría** (`createdBy`/`updatedBy`): un `AuditorAware<Long>` sobre `IdentityContext.get()` + `@EnableJpaAuditing`, mismo criterio *"por defecto, no por disciplina"*. El candado ArchUnit que exige `@CreatedBy`/`@LastModifiedBy` en toda entidad con `@TenantId` quedó **diseñado** aquí; lo **activa** el companion `update-patients` (`Patient`, la única entidad existente con `@TenantId`, es quien primero lo cumple — activarlo antes habría roto este mismo `verify`). Verificado con entidad de prueba real (`AutomaticAuditingIT`, ciclo de vida JPA, no consulta nativa). Estándar en `docs/backend.md §8.6`.
- ✅ **`organization-branding`** (negocio) — **construida**. Personalización visual por organización (color de marca y logo por URL) aplicada al **app shell** post-login; tema derivado del color de marca con foregrounds por contraste (WCAG AA), respeta dark mode y no altera los colores semánticos; edición solo `ADMIN`; logo por URL `https` con respaldo al logo del producto; el favicon sigue siendo el del producto. Spec: `openspec/specs/organization-branding/spec.md` · change archivado: `openspec/changes/archive/2026-07-11-add-organization-branding/`. Depende de `multi-tenancy` (✅).
  - ✅ **Extendida por el change `update-organization-branding`** (archivado: `openspec/changes/archive/2026-07-20-update-organization-branding/`; la spec modificó 2 requirements). El hex libre se reemplaza por un **catálogo curado de presets** pre-vetados para contraste AA — personalización controlada, no un editor de diseño; el modo de fallo "el admin eligió un color con mal contraste" deja de ser alcanzable desde la UI. `deriveTheme` no cambió su lógica; el backend valida solo la **clave** del preset (enum + `CHECK` de Postgres, probado con SQL crudo bypasseando JPA), el catálogo visual (hex + nombre) vive en el frontend para poder afinarlo sin migración. Verificado: el test que recorre todo el catálogo encontró un preset real (`EMERALD`) que no pasaba AA antes de commitear, y el contraste se confirmó correcto en **ambos** modos claro/oscuro por separado (no solo el agregado).
  - 📌 **Logo real + login sin gradiente decorativo** (ajuste de assets/estilo, sin change de OpenSpec — no cambia comportamiento ni contrato). `Logo.tsx` pasó del wordmark SVG interino al PNG real de Dentdesk (transparente, variantes `logo-light`/`logo-dark` según fondo); `AuthLayout` y el botón de `LoginForm`/`RegisterForm` (compartido en `AuthSubmitButton`) perdieron el gradiente cian→magenta por un morado plano fijo — el anti-patrón que `DESIGN.md` ya documentaba citando justo esta pantalla como precedente. Entrada con Framer Motion (fade + rise escalonado) y arcos ambientales en canvas (`SmileArcBackground`, respeta `prefers-reduced-motion`).
- ⏳ **Resto** (`staff`, `dental-chart`, `treatments`, `treatment-plans`, `appointments`, `patient-billing`, `images`, `inventory`, `dashboard`) — planeadas. *(`dental-chart` es el **odontograma** —el estado dental— y `treatments` las **acciones** que lo modifican; `treatment-plans` agrupa los tratamientos propuestos y su presupuesto; `staff` es la gestión del personal por el admin. Estas capacidades se recortaron **antes** de construirlas, ver §2.)*

- ✅ **`patients`** (negocio) — **construida**. La ficha clínico-administrativa: el **núcleo del dominio** (de ella cuelgan `dental-chart`, `treatments`, `appointments`, `images`, `patient-billing`) y la **primera entidad de negocio aislada por organización** — la **primera prueba real, sobre una entidad**, de la garantía de `update-multi-tenancy`: declaró su entidad siguiendo la convención y **no escribió ni una línea de código de aislamiento**. Alta, consulta, edición, **baja que archiva** (con reactivación) y listado con búsqueda; el documento es **único por clínica** (`ADR-0004`) y **emparejado con su tipo**; la minoría de edad se **deriva** y exige responsable; la anamnesis va en **JSONB dentro de la fila** (una tabla hija sin RLS sería un agujero). Spec: `openspec/specs/patients/spec.md` · change archivado: `openspec/changes/archive/2026-07-16-add-patients/`.
  - 📌 **Lección que deja**: la revisión humana **paró la implementación para auditar el modelo antes de fijar la tabla** — varios campos eran texto libre y debían ser conjuntos cerrados (`sex`, `documentType`, parentesco → **enum** en app + `CHECK` en BD + contrato). Regla que queda (design.md D8): **conjunto cerrado → enum; formato → validación en el boundary; nunca tipo numérico** para documentos ni teléfonos.
  - ✅ **Extendida por el change `update-patients` (autoría)** (archivado: `openspec/changes/archive/2026-07-19-update-patients/`; la spec ganó 1 requirement más). Aplica el mecanismo de `update-multi-tenancy` (autoría) a `Patient`: `created_by`/`updated_by`, alcance **decidido explícitamente como solo-persistencia** (no se expone en `PatientResponse` ni en la UI). Pacientes anteriores al mecanismo quedan con autoría desconocida (`NULL`), sin inventarla. **Activó el candado ArchUnit** que el companion solo diseñó (`Patient` fue la primera entidad que lo cumplió). Verificado con `PatientAuthorshipIT` (entidad real, ciclo de vida JPA).

**Pendientes sobre capacidades ya construidas** (salen de disolver `users`, ver §2 — son `update-*`, no capacidades nuevas):

- 📌 **`update-authentication`** — el **perfil del usuario**: `username` (único), `phone`, `country` y cambio de contraseña. Es dato de la **identidad**, así que amplía `authentication` en vez de inventar una capacidad. *(Estos campos ya estuvieron y se retiraron del registro en `add-multi-tenancy`; aquí vuelven, pero al perfil, no al alta.)*
- 📌 **Modelo de roles** — hoy solo hay `ADMIN` | `USER`, así que **la recepcionista y el odontólogo compartirían rol** y la recepcionista podría tocar el odontograma (un **acto clínico**). Se desdobla en `ADMIN` | `DENTIST` | `RECEPTIONIST`. **No es un change aparte**: lo lleva `add-staff` como delta sobre `multi-tenancy`, porque `staff` *es* asignar roles y no se puede especificar sin decidirlos. Ver la tabla de actores en `docs/domain.md`.
- 📌 **Selector de organización activa** — un usuario con varias membresías cambia de clínica, lo que **re-emite el token con otro `oid`**. Es una operación de **sesión**, no de gestión de usuarios. Será **otro** delta futuro sobre `multi-tenancy` (distinto del `update-multi-tenancy` ya archivado, que cerró el enganche del contexto).
- 📌 **Sin bloqueo por intentos fallidos de login** — anotación de seguridad, pendiente de aprobar antes de convertirla en change. `AuthService.login()` no lleva contador de intentos ni bloqueo de cuenta; `User` no tiene columnas para eso; no hay bucket4j/resilience4j en el `pom.xml`. `docs/architecture.md` ya lo anticipa ("rate limiting en memoria no funcionaría entre réplicas → necesitaría Redis"), pero **hoy no existe**. Amplía `authentication` (mismo actor: identidad/sesión). Las carreras que sí importan para auth ya están cubiertas — registro con email duplicado (unique constraint + 409 en `GlobalExceptionHandler`) y rotación de refresh token (bulk delete atómico) — el hueco es específicamente el conteo de intentos fallidos, que no existe porque nada lo necesita todavía.

> ⛔ **Bloqueo de secuencia:** ninguna capacidad de negocio (`staff`, `patients`, …) debe proponerse antes de archivar `multi-tenancy` (✅ ya archivada). Si una entidad de negocio nace sin organización, el error se arrastra a `appointments`, `treatments`, `images` y `patient-billing`.

---

## 4. Ciclo de vida de una capacidad (gobernanza)

```
planeada (este roadmap) → en progreso (change activo: proposal.md + design.md) → construida (specs/<cap>/spec.md; change en changes/archive/)
```

Disciplina para que el contexto de la IA se mantenga preciso (su contexto se extrae de `specs/` y `docs/`):

- **En `/opsx:propose`**: enlaza el change nuevo (`openspec/changes/<id>/proposal.md` y `design.md`) en "Estado de capacidades" y márcala 🚧/en progreso.
  - 📌 **Si la capacidad es NUEVA, su delta lleva el `## Purpose` desde el propose** (antes del `## ADDED Requirements`). El *objetivo* —el "para qué existe esta capacidad"— **tiene que pasar por la revisión humana como todo lo demás**. Si se escribe al archivar, aparece en un commit donde ya no hay nada que discutir: es la única parte del spec que se saltaría el ciclo de aprobación.
  - En un change **`update-*`** (capacidad ya construida) el delta **NO** repite el `Purpose`: ya vive en la spec. Solo se toca si el propósito de la capacidad cambia de verdad.
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
- **`treatments` antes que `treatment-plans`**: el plan organiza tratamientos existentes y genera su presupuesto; no redefine el procedimiento clínico ni el cobro al paciente.
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
