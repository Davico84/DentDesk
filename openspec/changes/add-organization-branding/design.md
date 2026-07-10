## Context

Personalización visual por organización sobre la base `multi-tenancy` (✅). El `proposal.md` fija el alcance; aquí se decide el **cómo**, y el corazón es la **derivación de un tema coherente a partir de 1-2 colores de marca** sin romper contraste ni el modo oscuro.

**Estado del que se parte:**
- Design system con **tokens CSS** en `modules/frontend/src/styles/globals.css`: ~24 variables como triplete HSL sin envoltura (`--primary: 292 72% 36%`), consumidas por Tailwind vía `hsl(var(--primary))`. Bloque `:root` (claro) + `.dark` (oscuro). Modo claro/oscuro ya alternable (`ThemeToggle`).
- Los tokens se agrupan en: **de marca** (`primary`, `ring`, `secondary`/`accent` + sus `-foreground`), **neutros** (`background`, `foreground`, `card`, `muted`, `border`, `input`; hoy con un leve sesgo de tono púrpura), y **semánticos** (`destructive`, `success`, `warning`).
- Sesión con **organización activa** (multi-tenancy); `RootLayout` ya hace *bootstrap* con splash antes de renderizar el shell.
- `OrganizationSettings` (1:1 con `Organization`, **sin RLS**) es el precedente exacto para el modelo de datos.

**Restricción de dominio:** la marca se aplica **post-login** (el login no conoce la organización; sin subdominio). Las pantallas de auth conservan la marca del producto.

## Goals / Non-Goals

**Goals**
1. Una organización con color de marca ve el app shell tematizado; una sin personalizar usa los defaults del producto — sin fila especial ni caso raro.
2. **Nunca** texto ilegible: los `-foreground` se eligen por contraste (WCAG AA ≥ 4.5:1), pase lo que pase con el color elegido.
3. El modo oscuro sigue funcionando con la marca aplicada.
4. Cambiar la marca (ADMIN) se refleja sin recargar y sin *flash* de tema por defecto.

**Non-Goals** — ver `proposal.md → Non-goals` (subida S3, email/PDF, login branded, paleta libre token-a-token, iconos de UI).

---

## Decisions

### D1. Modelo: `OrganizationBranding` 1:1, sin RLS (como `OrganizationSettings`)

```
organization_branding
  organization_id  PK/FK → organizations(id)   (comparte PK, @MapsId)
  brand_primary    varchar(9)  nullable   -- hex "#RRGGBB"
  brand_secondary  varchar(9)  nullable
  logo_url         varchar(2048) nullable
```

> **El favicon NO se personaliza.** Es siempre el del producto (`/favicon.svg`, el diente de OdontoRisas). Razones: los navegadores lo cachean de forma agresiva (la personalización funcionaría a medias), y el favicon actúa como identidad del producto en la pestaña. Ver `proposal.md → Non-goals`.

Todo **nullable**: ausencia de fila (o de campo) = default del producto. **Sin RLS** por organización, como `organization_settings`: es metadato del tenant (una fila por org, PK = `organization_id`), se accede por el `organization_id` del contexto. Es **forward-compatible** con una futura lectura pública por subdominio (una RLS estricta la bloquearía). Tabla nueva —no columnas en `OrganizationSettings`— porque es otro *concern* y otra pantalla de edición.

### D2. Se guardan colores **hex** crudos; la derivación vive en el **cliente**

El backend guarda `#RRGGBB` (lo que produce un color picker y pega un humano). **No** guarda tokens CSS ni deriva nada: la derivación es lógica de presentación. El frontend convierte hex→HSL y produce los ~24 tokens. Ventaja: el back no sabe de tokens; el algoritmo de tema evoluciona sin migраciones.

### D3. Derivación del tema (el núcleo)

A partir de `brandPrimary` (y `brandSecondary` opcional) se produce un mapa de tokens **para claro y oscuro**. Regla por grupo:

| Token | Cómo se deriva | Nota |
|---|---|---|
| `--primary` | `HSL(brandPrimary)` | el color de marca tal cual |
| `--primary-foreground` | **contraste** (D4): blanco o el `foreground` oscuro | jamás fijo |
| `--ring` | `H,S` de primary, `L` ajustada (+~9%, techo) | foco visible |
| `--secondary`, `--accent` | tinte **muy claro** del secundario (o del primario si no hay): `H₂, ~50%, 92–93%` | superficies suaves |
| `--secondary-foreground`, `--accent-foreground` | versión **oscura** del secundario: `H₂, ~84%, 20%` | contraste sobre el tinte |
| **neutros** (`background`, `foreground`, `card`, `muted`, `border`, `input`) | se conserva **S y L**; el **hue** se lleva al de la marca | sesgo cohesivo **sin** tocar contraste (S/L intactos) |
| **semánticos** (`destructive`, `success`, `warning`) | **intactos** | un "éxito" verde sigue verde aunque la marca sea roja |
| `--radius` | intacto | no es color |

Clave: al mover **solo el hue** de los neutros (manteniendo S y L), el UI se siente "de la marca" pero el contraste texto/fondo **no cambia** (depende de la luminancia ≈ L). Cero riesgo de accesibilidad ahí. El riesgo real se concentra en `primary-foreground`, que resuelve D4.

**Claro vs oscuro — *lightness clamping* (resuelve la elección de espacio de color):** se deriva en **HSL** (paridad directa con el sistema; permite modificadores de opacidad de Tailwind como `bg-primary/50`, que OKLCH complicaría). El `.dark` tiene sus propias luminancias, así que la derivación produce **dos mapas** ajustando la L del color de marca por modo para que siempre destaque legible sobre el fondo:

```
--primary en modo CLARO:   si L > 85%  →  L = 80%    (que no se pierda sobre el blanco)
--primary en modo OSCURO:  si L < 50%  →  L = 55–65% (que brille sobre el fondo oscuro #170d1a)
```

Así un púrpura muy oscuro sigue siendo púrpura pero **ilumina** lo suficiente en dark mode; un color casi blanco no desaparece en light mode. El *hue* y la *saturación* del color de marca no se tocan (sigue siendo "su" color); solo se acota la L al rango usable de cada modo.

**Color secundario (opcional) — con derivación por defecto:**
- **Si la organización lo define:** se convierte a HSL y alimenta `--secondary`/`--accent` (y sus `-foreground`) desde su *hue*.
- **Si es `null`:** se **deriva del primario** — mismo *hue*, saturación reducida y L alta (un tinte suave del primario), acotado a `L ≤ 96%` y `S ≤ 60%`. Así el UI se ve cohesivo y "premium" sin obligar a configurar dos colores.

### D4. Contraste garantizado (WCAG AA)

`primary-foreground` (y cualquier `-foreground` sobre color de marca) se elige por **luminancia relativa**:

```
lum(c)      = 0.2126·R + 0.7152·G + 0.0722·B   (canales linealizados)
contrast(a,b) = (max+0.05) / (min+0.05)         (≥ 4.5 para AA en texto)

foreground(brand) = argmax_{fg ∈ {blanco, foregroundOscuro}} contrast(fg, brand)
```

Se elige el foreground (blanco o el neutro oscuro del sistema) que **maximiza** el contraste; si el mejor no llega a 4.5:1, se usa igual pero el **editor avisa** ("este color no da suficiente contraste como color primario"). Así el sistema **nunca** pinta texto ilegible por diseño; a lo sumo advierte sobre una elección pobre.

**Invariante (explícito para evitar dudas futuras):** el sistema **únicamente alterna entre dos candidatos** —`blanco` y el `foreground` neutro del sistema— y **nunca genera colores de texto nuevos** (ni aclara/oscurece la marca para fabricar un foreground). Si mañana se quisieran más candidatos, sería un cambio de diseño consciente, no una consecuencia del algoritmo.

### D5. Aplicación en runtime: reactiva a la **organización activa**, no al login

El disparador del tema es el **`organizationId` activo de la sesión**, no el evento de login. Esto importa porque, cuando llegue el selector de organización (capacidad `users`), cambiar de clínica debe re-tematizar; si el hook se escribiera "al login", el tema de la organización anterior quedaría pegado.

```
useBranding()   key = session.organizationId
   │
   ├─ login ─────────────► GET branding(A) ─► deriveTheme ─► ThemeManager.apply(A)
   ├─ switch org ────────► GET branding(B) ─► deriveTheme ─► ThemeManager.apply(B)
   └─ logout ────────────► ThemeManager.reset()   (defaults del producto)
```

Flujo en el arranque (dentro del **splash de bootstrap ya existente**, así no hay *flash*):

```
boot ─► ThemeManager.apply(cache)        ← pintura inmediata (D8)
     ─► /auth/refresh ─► sesión con organizationId
     ─► GET /organizations/me/branding ─► deriveTheme ─► apply + actualiza cache
     ─► render del app shell
```

La marca se obtiene por un **endpoint dedicado** (`GET /organizations/me/branding`), no incrustada en la respuesta de auth: desacopla `authentication` de branding (el proposal declara "Modified Capabilities: ninguna") y permite **refetch** tras editar. El `ThemeToggle` sigue alternando claro/oscuro con la marca aplicada.

### D5.1. `ThemeManager`: el hook no toca el DOM

La manipulación del DOM se aísla en un módulo sin React, y el hook solo lo orquesta:

```
ThemeManager
  apply(theme)   // escribe CSS vars en :root y en el scope .dark
  reset()        // restaura los defaults del producto
  setLogo(url?)  // expone el logo activo (o el del producto)

useBranding()  ──►  ThemeManager.apply(deriveTheme(colors))
```

Beneficio: la lógica de tematización es **testeable sin React**, reutilizable (p. ej. la vista previa del editor la usa sin tocar la sesión) y desacoplada del ciclo de vida de los componentes.

### D6. Edición: `PUT /organizations/me/branding`, solo `ADMIN`

- `GET /organizations/me/branding` — cualquier usuario autenticado (para aplicar el tema de **su** organización activa).
- `PUT /organizations/me/branding` — **solo `ADMIN`** de la organización activa (autorización por rol, ya disponible desde el token). El `me` es la organización del contexto (nunca de la petición).
- Validación en el request: color `#RRGGBB` (regex) y `logoUrl` **solo `https://`** (ver D7); campos nullable (enviar `null` restaura el default de ese campo).
- Pantalla de ajustes (ruta ADMIN): color picker(s), input de URL del logo, y **vista previa en vivo** que aplica `deriveTheme` sin guardar.
- **Sin personalización = sin fila.** Si tras una actualización **todos** los campos quedan `null`, el servicio **elimina la fila** en vez de persistir una fila llena de nulls. Así el estado "esta organización no personaliza" tiene una sola representación (ausencia de fila), coherente con D1. `GET` devuelve siempre un objeto (campos `null` = defaults), exista o no la fila.
- Los DTOs llevan **ejemplos** (`@Schema(example = "#16C3B2")`) para que el contrato OpenAPI —y el cliente generado— sean autoexplicativos.

### D7. El logo es una URL externa: **segura** y **siempre con fallback**

El logo es una **URL a un recurso que la organización aloja**, con dos consecuencias:

**Seguridad (la URL la escribe un humano y se renderiza).** Para cerrar el vector de `javascript:`/`data:` y el contenido mixto:
- El backend valida **`https://` únicamente** (regex estricta; se rechaza `http://`, `javascript:`, `data:`, etc.), y el frontend repite la validación (Zod).
- Se renderiza **exclusivamente** con `<img src={logoUrl}>`. **Nunca** `dangerouslySetInnerHTML` ni inyección de la URL en el DOM como código.

**Disponibilidad (el admin puede borrar la imagen → `404`).**
- El `<img>` lleva `onError` → cae al **logo del producto**. Nunca se muestra un icono roto.

**Favicon:** fuera de alcance. Siempre el del producto (`/favicon.svg`); no se manipula el `<link rel="icon">` en runtime.

### D8. Pintura inmediata desde cache (`localStorage`), luego revalidación

El bootstrap es **serial** (`/auth/refresh` → token → `GET branding`), así que sin cache el usuario ve el splash con el tema por defecto durante ambos viajes. Patrón adoptado (habitual en SaaS):

```
localStorage ─► pinta el tema al instante ─► GET branding ─► si cambió, re-aplica y actualiza cache
```

Se cachea la marca **indexada por organización**, con la última organización activa:

```json
{ "activeOrgId": 123,
  "branding": { "123": { "brandPrimary": "#7f1aa6", "brandSecondary": null, "logoUrl": "..." } } }
```

Reglas:

- Al arrancar se pinta el tema de `activeOrgId` (pintura inmediata).
- Al volver `/auth/refresh` con el `organizationId` real: si **no coincide** con `activeOrgId`, `reset()` y aplica el correcto; si coincide, revalida (comparar-y-reemplazar el payload, ~200 B — sin `ETag`).
- **Se limpia en logout** (junto con el `reset()`).

**Límite honesto (por el diseño de sesión).** El access token vive **en memoria** y el refresh es **cookie httpOnly** (no legible por JS): en un arranque en frío **no se conoce el `organizationId` hasta que `/auth/refresh` responde**. Por eso la pintura desde cache es **optimista** (pinta la última organización activa) y solo puede corregirse *después* del refresh — no antes de montar. La alternativa (no pintar hasta el refresh) elimina el leak pero reintroduce el *flash* de tema por defecto; se elige pintar-y-corregir.

**Riesgo asumido (relevante en este dominio):** las clínicas **comparten estaciones**. Si una sesión termina sin logout (cierre del navegador, expiración) y luego entra otra persona/organización, el siguiente arranque pinta ~200 ms la marca anterior antes de corregirse tras el refresh. Cosmético; acotado por la limpieza en logout y la comparación de `organizationId`. Eliminarlo del todo exigiría exponer el `organizationId` a JS antes del refresh, lo que va contra el modelo de sesión (token en memoria + refresh httpOnly).

**Alternativa descartada:** incrustar el branding en la respuesta de auth. Eliminaría el round-trip *y* el parpadeo sin cache alguna, pero acopla `authentication` a branding (y el proposal declara "Modified Capabilities: ninguna").

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| Color de marca con **mal contraste** como primario | `-foreground` por luminancia (D4) + aviso en el editor; el texto nunca queda ilegible por defecto |
| **Flash** de tema por defecto tras login | Branding se aplica dentro del splash de bootstrap antes de renderizar el shell (D5) |
| Modo oscuro roto por la marca | Derivación produce mapa **claro y oscuro** con reglas de L por modo (D3) |
| Neutros re-tintados alteran contraste | Solo se mueve el **hue**; S y L intactos → contraste sin cambios (D3) |
| Branding leído sin RLS → listar el de otras orgs | Metadato no sensible (colores/URLs); se accede por `organization_id` del contexto; coherente con Settings (D1) |
| URL de logo apuntando a contenido malicioso | Validación de URL + se renderiza como `<img src>` (no se ejecuta); CSP recomendable a futuro |
| **El logo da 404** (el admin borró la imagen) | `onError` → fallback al logo del producto; nunca imagen rota (D7) |
| **Cambiar de organización deja el tema anterior** | El hook se cablea al `organizationId` activo, no al login (D5) |
| **Pintura de la marca anterior** en estación compartida | Cache clavada por `organizationId`, limpiada en logout y revalidada tras el refresh (D8) |
| Drift del contrato OpenAPI | `pnpm generate:api` en este change; gate de CI |

## Migration Plan

- `V8__organization_branding.sql`: `CREATE TABLE organization_branding (organization_id BIGINT PK REFERENCES organizations(id) ON DELETE CASCADE, brand_primary, brand_secondary, logo_url)` — todos nullable. Sin RLS (como `organization_settings`). Sin backfill (ausencia de fila = defaults).
- Reversible: `DROP TABLE`. No toca datos existentes.

## Open Questions

*(Resueltas en el diseño: espacio de color → **HSL con lightness clamping** (D3); uno o dos colores → **primario obligatorio + secundario opcional con derivación por defecto** (D3). No quedan preguntas abiertas; los umbrales exactos de clamping se afinan visualmente en el `apply`.)*

## Mejoras futuras (fuera del MVP, anotadas conscientemente)

- **Propagación en tiempo real.** Si un administrador cambia la marca mientras otro usuario tiene la aplicación abierta, este la verá en su siguiente arranque/refresco, no al instante. Propagar en vivo exige **push** (WebSocket/STOMP —ya en el stack— o polling): es un problema de *propagación*, no de versionado.
- **`updated_at` / `ETag` + `If-None-Match`.** Permitirían validar la cache sin traer el cuerpo. Hoy no aportan: el payload son 4 campos (~200 B) y la revalidación es un simple comparar-y-reemplazar (D8). Además, un `updated_at` **choca con D6** ("sin personalización = sin fila"): al borrar la fila no queda dónde vivir el timestamp. Si más adelante se adopta, habría que decidir entre conservar la fila o mover el timestamp a `organizations`.
- **CSP para recursos externos** (el logo alojado fuera).
- **Favicon por organización**: descartado hoy (los navegadores lo cachean agresivamente y el favicon es identidad del producto). Si algún día se retoma, requeriría manipular `<link rel="icon">` y aceptar que la actualización no es inmediata.
