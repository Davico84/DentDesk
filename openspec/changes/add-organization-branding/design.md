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
  favicon_url      varchar(2048) nullable
```

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

**Claro vs oscuro:** el `.dark` tiene sus propias luminancias; la derivación produce **dos mapas** (uno con las reglas de L de `:root`, otro con las de `.dark`) y ambos se inyectan. Un color de marca demasiado oscuro para fondo en dark mode se corrige subiendo su L en el mapa oscuro (regla de lightness por modo).

### D4. Contraste garantizado (WCAG AA)

`primary-foreground` (y cualquier `-foreground` sobre color de marca) se elige por **luminancia relativa**:

```
lum(c)      = 0.2126·R + 0.7152·G + 0.0722·B   (canales linealizados)
contrast(a,b) = (max+0.05) / (min+0.05)         (≥ 4.5 para AA en texto)

foreground(brand) = argmax_{fg ∈ {blanco, foregroundOscuro}} contrast(fg, brand)
```

Se elige el foreground (blanco o el neutro oscuro del sistema) que **maximiza** el contraste; si el mejor no llega a 4.5:1, se usa igual pero el **editor avisa** ("este color no da suficiente contraste como color primario"). Así el sistema **nunca** pinta texto ilegible por diseño; a lo sumo advierte sobre una elección pobre.

### D5. Aplicación en runtime: `useBranding` durante el bootstrap

```
Login/refresh OK ──► sesión con organización activa
        │
        ▼  (RootLayout: splash de bootstrap)
   GET /organizations/me/branding  ─► { brandPrimary, brandSecondary, logoUrl, faviconUrl }
        │
        ▼  deriveTheme(colors) → { light: {...}, dark: {...} }
   aplica CSS vars en :root y .dark  +  swap <link rel="icon">  +  logo en el header
        │
        ▼  recién entonces se renderiza el app shell   (sin flash de tema por defecto)
Logout ──► reset a defaults del producto (quita overrides, restaura favicon)
```

Se aplica **dentro del splash de bootstrap ya existente** (no hay *flash* de tema por defecto). La marca se obtiene por un **endpoint dedicado** (`GET /organizations/me/branding`), no incrustada en la respuesta de auth: desacopla `authentication` de branding y permite **refetch** tras editar. Los tokens se inyectan como propiedades CSS en `document.documentElement` (`:root`) y en un scope `.dark`; el `ThemeToggle` sigue alternando entre ambos.

### D6. Edición: `PUT /organizations/me/branding`, solo `ADMIN`

- `GET /organizations/me/branding` — cualquier usuario autenticado (para aplicar el tema de **su** organización activa).
- `PUT /organizations/me/branding` — **solo `ADMIN`** de la organización activa (autorización por rol, ya disponible desde el token). El `me` es la organización del contexto (nunca de la petición).
- Validación en el request: color `#RRGGBB` (regex) y URL `http(s)` bien formada; campos nullable (enviar `null` restaura el default de ese campo).
- Pantalla de ajustes (ruta ADMIN): color picker(s), inputs de URL de logo/favicon, y **vista previa en vivo** que aplica `deriveTheme` sin guardar.

### D7. Favicon dinámico

`useBranding` sustituye el `href` del `<link rel="icon">` (o lo crea) con `faviconUrl`; al logout lo restaura al del producto (`/favicon.svg`). Nota: algunos navegadores cachean el favicon agresivamente; es aceptable (se resuelve al recargar).

---

## Risks / Trade-offs

| Riesgo | Mitigación |
|---|---|
| Color de marca con **mal contraste** como primario | `-foreground` por luminancia (D4) + aviso en el editor; el texto nunca queda ilegible por defecto |
| **Flash** de tema por defecto tras login | Branding se aplica dentro del splash de bootstrap antes de renderizar el shell (D5) |
| Modo oscuro roto por la marca | Derivación produce mapa **claro y oscuro** con reglas de L por modo (D3) |
| Neutros re-tintados alteran contraste | Solo se mueve el **hue**; S y L intactos → contraste sin cambios (D3) |
| Branding leído sin RLS → listar el de otras orgs | Metadato no sensible (colores/URLs); se accede por `organization_id` del contexto; coherente con Settings (D1) |
| URL de logo/favicon apuntando a contenido malicioso | Validación de URL + se renderiza como `<img src>`/favicon (no se ejecuta); CSP recomendable a futuro |
| Drift del contrato OpenAPI | `pnpm generate:api` en este change; gate de CI |

## Migration Plan

- `V8__organization_branding.sql`: `CREATE TABLE organization_branding (organization_id BIGINT PK REFERENCES organizations(id) ON DELETE CASCADE, brand_primary, brand_secondary, logo_url, favicon_url)` — todos nullable. Sin RLS (como `organization_settings`). Sin backfill (ausencia de fila = defaults).
- Reversible: `DROP TABLE`. No toca datos existentes.

## Open Questions

1. **Espacio de color de la derivación.** HSL es lo que usa el sistema, pero OKLCH da mezclas y contraste perceptualmente más uniformes. ¿Derivar en HSL (simple, sin dependencias) u OKLCH (mejor, ¿lib?)? Propuesta: HSL en el MVP; OKLCH si el resultado visual no convence.
2. **¿Un color de marca o dos?** El MVP admite primario (obligatorio para personalizar) + secundario opcional. Confirmar si el secundario aporta suficiente o si con el primario basta para el MVP.
3. **Persistencia del tema entre recargas antes del bootstrap.** ¿Cachear los colores en `localStorage` para pintar el tema al instante (antes del GET) y evitar cualquier parpadeo? Trade-off: velocidad vs una copia del tema fuera de la sesión.
4. **Formato de `favicon`.** ¿Aceptar solo imágenes rasterizadas (png/ico) o también SVG? (afecta el `type` del `<link>`).
