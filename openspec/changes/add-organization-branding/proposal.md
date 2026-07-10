## Why

Ahora que OdontoRisas es un **SaaS multi-tenant**, cada organización (clínica) quiere verse como *suya*: su color de marca, su logo, su favicon. Es una expectativa estándar de un SaaS y un diferenciador claro de la capa de plataforma reutilizable. La base multi-tenant ya lo habilita —hay una organización activa por sesión y un design system basado en tokens CSS—, así que la personalización cuelga naturalmente de `Organization`, igual que `OrganizationSettings`.

## What Changes

- **Nueva capacidad `organization-branding`**: cada organización define su **marca visual** (color primario, color secundario opcional, logo, favicon). El **app shell** (todo lo posterior al login) se re-tematiza con la marca de la organización activa; una organización que no personaliza usa los valores por defecto del producto (teal `#16C3B2` / púrpura `#8A1A9B`).
- **Entidad `OrganizationBranding`** 1:1 con `Organization` (como `OrganizationSettings`), campos nullable (null = default del producto).
- **API**: leer la marca de la organización activa; actualizarla (solo rol `ADMIN` de la organización).
- **Derivación de tema en el cliente**: a partir de 1-2 colores de marca se derivan los ~24 tokens del design system, eligiendo los colores de texto (`-foreground`) por **contraste** (WCAG AA) para no romper legibilidad ni el modo oscuro. Los tokens **semánticos** (éxito/aviso/error) y los **neutros** no cambian con la marca.
- **Favicon dinámico**: se sustituye el `<link rel="icon">` en runtime, post-login.
- **Pantalla de ajustes de marca** (solo ADMIN): elegir color(es), pegar URL de logo y favicon, con vista previa en vivo.
- **Contrato OpenAPI** regenerado (`pnpm generate:api`).

**Decisiones de alcance ya cerradas** (a detallar en `design.md`):

- **Logo por URL** (`logoUrl`): el admin pega una URL ya alojada. **Sin** subida de archivos a S3 ni dependencia de la capacidad `images` (se difiere).
- **Paleta guiada, no libre** (enfoque C): la organización elige **un** color primario de marca (secundario opcional); el resto se deriva o se mantiene fijo. Evita que una elección de color rompa el contraste o el dark mode.
- **Solo estética** (app shell). Nada de email/PDF/facturas con marca por ahora.
- **La marca se aplica DESPUÉS del login.** Las pantallas de auth (login/registro) conservan la marca del producto (OdontoRisas): antes de autenticar no se conoce la organización (login por email, sin subdominio).
- **Aislamiento como `OrganizationSettings`**: `OrganizationBranding` es metadato del tenant (una fila por org, PK = `organization_id`), **sin** RLS por organización; se accede por el `organization_id` del contexto. Coherente con Settings y forward-compatible con una futura lectura pública por subdominio.
- **Sin personalización = sin fila**: si el administrador vacía todos los campos, se elimina la fila (un solo estado para "no personaliza").
- **El tema reacciona a la organización activa**, no al login: cuando llegue el selector de organización (`users`), cambiar de clínica re-tematiza.
- **Logo y favicon siempre con respaldo**: son URLs externas que pueden dar `404`; el sistema degrada al recurso del producto sin mostrar nada roto.

**Mejoras anotadas fuera del MVP**: propagación en tiempo real de la marca a sesiones abiertas (requiere push, no versionado), `ETag`/`If-None-Match` (payload de ~200 B; hoy no aporta), y CSP para recursos externos.

## Capabilities

### New Capabilities

- `organization-branding`: marca visual por organización (colores de marca, logo, favicon) aplicada al app shell en runtime, con edición por el administrador de la organización. Capacidad de **negocio** montada sobre `multi-tenancy`.

### Modified Capabilities

- *(ninguna)* — no cambia el comportamiento de capacidades ya construidas. La sesión ya expone la organización activa; el tema se aplica en el cliente sin tocar `authentication`.

## Non-goals

- **Subida de archivos** (logo/favicon como archivo): se usa URL. La subida a S3 llega con `images`.
- **Marca en email/PDF/facturas**: solo estética del app shell por ahora.
- **Login/registro branded por organización**: requiere subdominio por organización (`acme.odontorisas.com`), *non-goal* heredado de `multi-tenancy`.
- **Paleta totalmente libre / tema completo editable token a token**: se personaliza el/los color(es) de marca; neutros y semánticos son fijos.
- **Personalizar el set de iconos de UI** (lucide): "iconos" aquí = logo + favicon, no la iconografía de la interfaz.
- **Temas por usuario** (aparte del modo claro/oscuro ya existente): la marca es por organización, no por persona.

## Impact

**Base de datos** (`modules/backend/src/main/resources/db/migration/`, hoy en `V7`)
- `V8__organization_branding.sql`: tabla `organization_branding` (`organization_id` PK/FK → `organizations`, `brand_primary`, `brand_secondary`, `logo_url`, `favicon_url`, todos nullable). Sin RLS (como `organization_settings`). Backfill innecesario (ausencia de fila = defaults).

**Backend** (`modules/backend`)
- Nuevo: `persistence/entity/OrganizationBranding.java` (+ repositorio), `service/organization` (leer/actualizar), `presentation` (DTOs + endpoints `GET`/`PUT /organizations/me/branding`, `PUT` restringido a `ADMIN`).
- Validación de formato de color (hex/HSL) y de URL en el request.

**Contrato y frontend** (`contracts/openapi.json`, `modules/frontend`)
- Regenerar contrato + cliente orval.
- Nuevo: utilidad de **derivación de tema** (color de marca → tokens CSS, con foregrounds por contraste), hook `useBranding` (aplica CSS vars en `:root`/`.dark` + favicon al establecer sesión; resetea en logout), y **pantalla de ajustes de marca** (ADMIN) con vista previa.
- La marca activa se expone en la sesión (o vía endpoint de branding) para aplicarla tras el login.

**Documentación**
- `docs/domain.md` (entidad `OrganizationBranding`), `docs/frontend.md` (mecanismo de tematización por organización), `docs/vision.md` (capacidad construida) — al archivar.

**Dependencias y secuencia**
- Depende de `multi-tenancy` (✅ construida). El rol `ADMIN` que edita la marca ya existe desde el registro, así que **no** depende de `users`; se recomienda situarla **después de `users`** en el roadmap, pero es construible de forma independiente.
