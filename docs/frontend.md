# Estándares de Frontend — OdontoRisas

Dimensión **tecnológica** (reutilizable). Guía única del cliente: stack, arquitectura de pantallas/módulos, capa de datos (React Query + Axios), estado de cliente (Zustand) y patrones de UI. El **estilo general de código** (TypeScript, nombres, legibilidad) vive en `docs/coding-style.md`; el **stack cerrado y versiones** en `docs/architecture.md §3`; las **pruebas** en `docs/testing.md`.

---

## 1. Stack y arquitectura de pantallas/módulos

**Stack** (detalle y versiones en `docs/architecture.md §3`): React 19 · TypeScript · Vite. UI: Tailwind + shadcn/Radix, `lucide-react`, `framer-motion`, `sonner`. Datos de servidor: `@tanstack/react-query` + `axios`. Estado de cliente: Zustand. Formularios: `react-hook-form` + `zod`. Routing: `react-router-dom` v7 con guards centrales `RequireAuth`/`RequireGuest` (roles `ADMIN`/`USER`; sin `roles` = cualquier autenticado). Gestor: pnpm.

> **Estado actual (objetivo, aún no adoptado):** `@base-ui/react` (§4.1), `recharts` (gráficas) y el
> par realtime `@stomp/stompjs`/`sockjs-client` (`docs/vision.md §7`) figuran en el stack **cerrado**
> (`docs/architecture.md §3`) pero **no están instalados todavía**: se agregan la primera vez que una
> capacidad los necesite de verdad, no antes — mismo criterio que ya se aplicó con `framer-motion`
> (§4.2, instalado cuando `patients` lo necesitó de verdad para animar el listado).

**Estructura** (`modules/frontend/src/`):

```
src/
  layouts/     # marcos de página (shells) reutilizables
  screens/     # pantallas: orquestan una feature dentro de un layout (hojas de composición)
  modules/     # lógica encapsulada por dominio
    core/      # transversal (SIN deps a otros módulos):
      #   auth/ (guards RequireAuth/RequireGuest, roleHome) · components/ (DataState, InfoCard…)
      #   config/ (httpClient axios) · hooks/ (useDebouncedValue…) · services/generated/ (orval)
      #   theme/ (deriveTheme, ThemeManager — branding) · ui/ (shadcn) · utils/
    <cap>/     # un módulo por capacidad (hooks de dominio, components, types, schemas Zod)
  store/       # stores Zustand (estado de cliente)
  routes/      # definición de rutas + guards (RequireAuth/RequireGuest)
  styles/      # globals.css y variables de tema
```

**Reglas de arquitectura:**
- **Un solo `AppLayout` autenticado, no un layout por rol.** ADMIN y USER son roles dentro de la
  **misma** clínica (mismo staff, mismo shell), no personas con experiencias distintas — ADMIN es
  un superset de USER (hoy, solo suma `Personalizar marca`). El shell (sidebar + header) es único;
  la navegación se filtra por rol dentro de él, igual que ya filtran por rol las rutas
  (`RequireAuth roles={...}`). Separar en `AdminLayout`/`UserLayout` solo se justifica si el admin
  se vuelve una experiencia realmente distinta (p. ej. un backoffice de plataforma que gestiona
  *otras* organizaciones) — no es el caso hoy, y adelantarlo sería la abstracción prematura que
  `docs/coding-style.md §3` prohíbe.
  > **Estado actual (objetivo, aún no adoptado):** hoy solo existe `RootLayout` (bootstrap de
  > sesión + marca, sin nav — cada pantalla arma su propio `<main>` suelto). El `AppLayout` con
  > sidebar/header se construye la primera vez que una segunda pantalla necesite nav persistente
  > compartida, no antes.
- **`screens` orquestan; `modules` encapsulan lógica.** Las pantallas montan features dentro de un layout; no contienen lógica de negocio.
  > [!IMPORTANT]
  > **Una pantalla es una línea.** `screens/<área>/XScreen.tsx` → `return <XFeature />;`. **NO declares sub-componentes dentro de la screen** —ni skeletons, ni estados vacíos/error, ni campos de formulario, ni filas—: van a `modules/<cap>/components/`, agrupados por tipo (la feature, filas, loaders/skeletons, estados, modales). Señal de mal uso: una screen con `function SkeletonRows()` o `function Field()` debajo.
  > ```tsx
  > // ✅ screens/patients/PatientListScreen.tsx
  > export function PatientListScreen() { return <PatientList />; }
  > // ❌ una screen con su tabla, su skeleton y su estado vacío declarados dentro
  > ```
- **Un archivo = un componente.** La regla de arriba **no es solo para las screens**: un componente de módulo tampoco declara otros dentro. **Skeletons, estados (vacío/error/no encontrado), filas, modales y campos van SIEMPRE a su propio archivo**, aunque solo los use un sitio. Los helpers de presentación, a su `xDisplay.ts`.
  > ✅ **Lo impide la build, no la disciplina.** `react/no-multi-comp` (ESLint) está activa en `src/screens/**` y `src/modules/*/components/**`: **un archivo con dos componentes no compila**. Es el equivalente frontend del *tripwire* de ArchUnit del backend — esta regla ya estaba escrita y se incumplió tres veces, así que dejó de ser un consejo. `core/ui` está **exento** a propósito (la excepción de la familia de primitivos, abajo).
  > [!IMPORTANT]
  > Señal de mal uso: `PatientDetail.tsx` con `function PatientDetailSkeleton()` debajo. **Un skeleton nunca vive dentro del componente que reemplaza** — es una unidad independiente (se prueba y se reutiliza sola), y enterrarlo obliga a leer la feature entera para encontrarlo.
  > ```
  > ✅ PatientDetail.tsx · PatientDetailSkeleton.tsx · PatientDetailStates.tsx · patientDisplay.ts
  > ❌ PatientDetail.tsx con el skeleton, el "no encontrado" y dos helpers dentro
  > ```
  > **Única excepción:** una **familia de primitivos** de `core/ui` diseñada para usarse junta puede compartir archivo, siguiendo la convención de shadcn (`dialog.tsx` exporta `Dialog`, `DialogContent`, `DialogHeader`…). No es excusa para meter la feature y su skeleton en el mismo sitio.
- **`core` no depende de otros módulos** (es la base transversal: UI, auth guard, utils). Los demás módulos pueden depender de `core`.
  > [!IMPORTANT]
  > **Un componente genérico se crea EN `core`, no en el módulo.** Si **no conoce el dominio** (un `FormField`, un `SelectField`, un `InfoCard`, un `Badge` base) → `core/ui` (primitivo) o `core/components` (compuesto), y **genérico** (`<T extends FieldValues>` si aplica). Solo lo **específico del dominio** (p. ej. `PatientStatusBadge`, que conoce el enum del paciente) vive en `modules/<cap>/components/`, **componiéndose** sobre el primitivo de `core`. Pregunta de control: *"¿esto lo usaría otra capacidad?"* → sí = `core`.
- **La validación de formularios vive en el schema Zod del módulo** (`modules/<cap>/schemas/`), **nunca dentro del componente**: ahí van las reglas (incluidas las condicionales, con `superRefine`), en **paridad con el backend** (`docs/coding-style.md §7`). El componente de campo **solo pinta** el error —venga de Zod o del servidor (`applyServerFieldErrors`)—. El servidor es la autoridad.
  > [!IMPORTANT]
  > **`useForm({ mode: "onTouched" })` — avisa tarde, corrige pronto.** La primera validación es **al salir** del campo (no le grites "correo inválido" a quien lleva una letra escrita); pero una vez avisado, **revalida en cada tecla**, así el error **desaparece solo** en cuanto el valor pasa a ser válido.
  > **No uses `"onBlur"`**: obliga a salir del campo para descubrir que ya lo arreglaste — el mensaje se queda en pantalla mintiendo mientras corriges. Y **no uses `"onChange"`**: valida desde la primera tecla, así que te marca en rojo por no haber terminado de escribir.
- **Mapeo capacidad ↔ código**: cada capacidad de negocio `<cap>` se materializa en `modules/<cap>/` (hooks de dominio, components, schemas Zod) y sus pantallas en `screens/<área>/`. El **cliente API tipado se genera** en `core/services/generated/` (ver §2.3); lo transversal (guard, roles) en `modules/core/`.

---

## 2. React Query y capa de API

Cómo interactuar con el backend y gestionar el **estado del servidor** con **TanStack Query (React Query)** y **Axios**.

### 2.1 Reglas generales del estado del servidor
- **Estado único**: todos los datos del backend pertenecen a React Query. **No** se duplican en Zustand.
- **Control de estados**: todo componente que consuma datos del servidor maneja explícitamente carga (`isLoading`/`isPending`), error (`error`/`isError`) y vacío (`empty`).

### 2.2 Convención de Query Keys
Cada dominio/módulo implementa una **factoría de Query Keys** en sus hooks:

```typescript
export const userKeys = {
  all: ["user"] as const,
  simpleProfile: () => [...userKeys.all, "simple-profile"] as const,
  fullProfile: () => [...userKeys.all, "full-profile"] as const,
};
```

- **Clave raíz (`all`)**: única por módulo, `["nombre-modulo"] as const`.
- **Subclaves**: funciones que extienden el array raíz por desestructuración (mantienen la jerarquía de cache).
- **Invalidación coherente**: `queryClient.invalidateQueries({ queryKey: userKeys.all })` refresca todas las subclaves.

> **Estado actual (objetivo, aún no adoptado):** los hooks existentes (`auth`, `branding`, `patients`) usan una **clave raíz inline** (`const BRANDING_KEY = ["branding", "me"] as const`) e invalidan por ella, no una factoría `xKeys`. Es la convención **real** hoy, y mantiene la jerarquía. Adopta la factoría cuando un módulo tenga **varias** lecturas con parámetros y la invalidación por subclaves aporte de verdad.

### 2.3 Estructura de la capa de API (generada con orval)

La capa de API se divide en dos: orval genera los **tipos + funciones fetch tipadas** desde el contrato; los **hooks de TanStack Query se escriben a mano** (tu estilo) sobre esas funciones. **No** se escriben clientes axios a mano ni tipos a mano. Flujo:

```
Pantalla ──> hook de dominio (modules/<cap>/hooks, a mano) ──> fetcher generado (orval) ──> mutator axios (core) ──> API
```

- **Generado (core)**: `core/services/generated/` contiene los **tipos DTO** (en `generated/model/`) y las **funciones fetch tipadas**, un archivo **por capacidad** (`generated/<cap>.ts`, p. ej. `auth.ts` con `register`, `login`, `refresh`, `logout`), producidos por orval (`client: axios-functions`) desde `contracts/openapi.json`. Es código de máquina: **versionado pero no se edita a mano** (`pnpm generate:api`). Excluido de ESLint; se valida con `tsc`.
- **HTTP base / transporte (core, a mano)**: `core/config/httpClient.ts` — la instancia axios (`withCredentials`, inyección de `Bearer`, interceptor de refresh) que orval usa como *mutator*. Es **infraestructura de transporte**, no un service de dominio ni un util.
- **Hooks de dominio (módulo, a mano)**: `modules/<cap>/hooks/` — aquí vive **tu estilo**: `useQuery`/`useMutation` con query-key factory (§2.2), validación Zod en el boundary (§2.5), toasts e invalidación. El `queryFn`/`mutationFn` llama a la **función generada** (tipada), no a un axios a mano. Junto con `schemas/` (Zod de formularios), `components/` y `types/` propios.
- **Componentes limpios**: ninguna pantalla importa `axios` directamente; usan los hooks de dominio.
- **Contratos tipados**: tipos y llamadas HTTP salen del contrato (orval), **nunca se teclean a mano**. Ver `api-type-contracts` y `docs/coding-style.md §7` (paridad de validación).

### 2.4 Ejemplo (fetcher generado → hook a mano → pantalla)

```typescript
// modules/auth/hooks/useAuth.ts — hooks de dominio a mano (tu estilo), sobre los fetchers generados
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { login } from "@/modules/core/services/generated/auth";        // fetcher tipado (del contrato)
import type { LoginRequest } from "@/modules/core/services/generated/model";
import { useSessionStore } from "@/store/useSessionStore";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";

export function useLogin() {
  const setSession = useSessionStore((s) => s.setSession);
  return useMutation({
    mutationFn: (data: LoginRequest) => login(data),                    // en vez de un AuthService a mano
    onSuccess: (auth) => setSession(auth.accessToken, auth.user),
    onError: (e) => toast.error(getUserFriendlyError(e)),
  });
}
```
```tsx
// uso en la pantalla
const { mutate: login, isPending } = useLogin();
// login({ email, password });
```

> Para lecturas (GET), el hook a mano usa `useQuery` con su **query key** (§2.2) y validación Zod en el `queryFn` (§2.5), llamando al fetcher generado.

### 2.5 Validación de respuestas en el *boundary* (Zod)
> [!IMPORTANT]
> El tipado TS es solo en compilación; la respuesta del backend es dinámica en runtime. Si el backend renombra/elimina un campo, el tipo "miente" y revienta en la UI. Por eso **validamos la respuesta con Zod en el `queryFn`** (donde los datos entran a la app).

- Define un **schema Zod** del DTO (en `schemas/`), validando **estrictamente lo que la UI consume** (`z.object` strippea claves desconocidas, no falla por ellas).
- En el `queryFn`, valida con `parseApiResponse` (`modules/core/utils/validateApiResponse.ts`): si el shape no calza, lanza error controlado → React Query lo expone como `isError`.

```typescript
queryFn: async () => {
  const data = await ContactsService.listContacts(params);
  parseApiResponse(contactsResponseSchema, data, "contacts.list");
  return data;
},
```
- **Solo en lecturas que la UI renderiza** (adopción incremental); **no sobre-valides el envelope** (metadata de paginación). A futuro, inferir tipos desde los schemas (`z.infer`) para una única fuente de verdad.

> **Estado actual (objetivo, aún no adoptado):** `modules/core/utils/validateApiResponse.ts` (`parseApiResponse`) **todavía no existe** y ningún hook valida la respuesta con Zod. Los hooks actuales confían en el tipo del contrato (que el `codegen-drift` mantiene sincronizado). Cuando se adopte, se crea primero el util y se aplica **incrementalmente** a las lecturas que la UI renderiza.

---

### 2.6 Errores de la API — tipados desde el contrato

**Nunca escribas a mano la forma de un error.** El backend responde RFC 9457 y el contrato lo documenta, así que orval genera los tipos (`ApiProblem`, `ValidationProblem`, `FieldError`). Si el backend cambia la forma del error, lo detecta el **typecheck** en vez de romperse en producción.

> Antes había literalmente un `interface ProblemDetailBody { detail?: string }` escrito a mano. Eso es lo que la capacidad `api-type-contracts` existe para impedir. Si te descubres declarando la forma de un error, **el contrato es el que está incompleto**: arréglalo allí.

En axios el cuerpo del error viaja en `AxiosError<T>`, así que se **parametriza con el tipo generado** (sigue siendo derivado del contrato). Todo pasa por `modules/core/utils/apiError.ts`:

| Helper | Para qué |
|---|---|
| `getUserFriendlyError(e)` | El `detail` del problema, redactado para el usuario → aviso genérico (toast) |
| `getFieldErrors(e)` | Los `errors[{field, message}]` del `400` de validación |
| `applyServerFieldErrors(e, setError)` | Los vuelca **sobre los campos** de react-hook-form |

**Errores de validación → junto al campo.** Un `400` del servidor trae el detalle por campo: se pinta **en el input**, igual que los de Zod. En la mutación:

```ts
mutation.mutate(values, {
  onError: (error) => applyServerFieldErrors(error, setError),
});
```

`errors` es **opcional**: el otro `400` posible (JSON malformado) no lo trae. Por eso `getFieldErrors` devuelve `[]` y la UI cae en el aviso genérico.

---

## 3. Estado del cliente (Zustand)

### 3.1 Cuándo usar Zustand
Solo para **estado de cliente global**: filtros de tabla activos, ordenamiento, datos de sesión guardados localmente (evitar parpadeos), selecciones temporales en memoria.

**Cuándo NO**: no dupliques datos de API (eso es React Query); no guardes estado redundante que puedas derivar en render/`useMemo`.

### 3.2 Convención y estructura de stores
Todos en `src/store/`. Cada store: 1) interfaz `[Feature]State` (estado + acciones tipadas), 2) creación con `create`, 3) **exportación única envuelta en `createSelectors`** (`use[Feature]Store`).

```typescript
import { create } from "zustand";
import { createSelectors } from "./createSelectors";

interface SortingState {
  orderBy: "asc" | "desc";
  sortBy: string;
  setOrderBy: (order: "asc" | "desc") => void;
  setSortBy: (field: string) => void;
  resetSorting: () => void;
}

const useSortingStoreBase = create<SortingState>((set) => ({
  orderBy: "asc",
  sortBy: "createdAt",
  setOrderBy: (order) => set({ orderBy: order }),
  setSortBy: (field) => set({ sortBy: field }),
  resetSorting: () => set({ orderBy: "asc", sortBy: "createdAt" }),
}));

export const useSortingStore = createSelectors(useSortingStoreBase);
```

### 3.3 Rendimiento
> [!IMPORTANT]
> **Regla no negociable: nunca consumas el store completo, siempre por selector.** `const { a, b } = useStore()` suscribe a *cualquier* cambio y provoca renders innecesarios.

- **`createSelectors`** (`src/store/createSelectors.ts`): genera selectores atómicos `store.use.<campo>()`, forma recomendada de consumir.
  ```tsx
  const user = useUserStore.use.user();
  const setUser = useUserStore.use.setUser();
  ```
- **Selector explícito** cuando no uses el helper: `useSortingStore((s) => s.sortBy)`.
- **`useShallow`** (de `zustand/react/shallow`) para seleccionar varios campos en un objeto sin renders por referencia nueva.
- **Múltiples stores pequeños** enfocados en vez de uno monolítico.

### 3.4 Separación de responsabilidades
- **Un store = estado + acciones.** Sin lógica de negocio, sin valores derivados, sin estado de servidor.
- **Preferir el middleware `persist`** (de `zustand/middleware`) sobre `localStorage` manual: declarativo, hidrata al iniciar, centraliza errores de serialización.
- **La orquestación vive en hooks**, no en componentes (patrón "componente tonto + hook inteligente").

---

## 4. Patrones de UI, animación, responsividad y accesibilidad

> **Superficie nueva sin patrón previo** (ej. la futura landing pública de `public-booking`,
> `docs/vision.md §7`, o un rediseño deliberado de marca): antes de picar código, dirección creativa
> con el skill `frontend-design` — anclada en la identidad ya existente (gradiente + logo), no una
> reinvención desde cero. **Pantalla dentro del producto ya autenticado**: no se usa; se sigue el
> sistema de diseño de esta sección (§4.1–§4.6) y `coding-style.md §5.5` ("consistencia > preferencia
> personal").

### 4.1 shadcn/ui e integración
> [!IMPORTANT]
> **Antes de crear un componente visual o añadir una librería**, revisa si ya existe en `modules/core/ui/`. Reutilizar garantiza temas (claro/oscuro), overrides de marca, estilos consistentes (bordes, sombras, transiciones, accesibilidad) y evita duplicación y bundle innecesario.

> [!IMPORTANT]
> **Nunca uses el atributo nativo `title`** en un elemento interactivo: cuando exista un componente `Tooltip` en `core/ui`, úsalo. Mantén siempre `aria-label` (el tooltip no lo sustituye).

**Estado actual de `modules/core/ui/`** (lo que HAY hoy): `button` (+ `button-variants`), `card`, `input`, `icon-input`, `password-input`, `label`, `logo`, `theme-toggle`, `form-field` (`FormField`/`SelectField`/`FormSection`, genéricos sobre react-hook-form), `dialog` (shadcn/Radix), `confirm-dialog` y `tooltip` (shadcn/Radix). Para un tooltip de ayuda sobre un control usa **`HintTooltip`** (`core/components`): trae su propio `TooltipProvider` y, con `triggerDisabled`, envuelve un botón deshabilitado para que el tooltip se siga abriendo (un `disabled` no recibe hover) — justo cuando más falta explicar por qué no está disponible.

**Por incorporar** (aún NO existen; añádelos con `pnpm dlx shadcn@latest add <componente>` desde el registro oficial, la **primera vez que una pantalla los necesite**): `sheet`, `popover`, `dropdown-menu`, `select`, `table`, `badge`.

> **`@base-ui/react` (headless, del stack §1) tampoco está instalado.** El único primitivo headless
> hoy es `@radix-ui/react-dialog` (vía shadcn). Instala `@base-ui/react` recién cuando un componente
> lo necesite y shadcn/Radix no lo cubra — no antes.

> [!WARNING]
> **`components.json` ya existe (escrito a mano) — no dejes que el CLI haga `init`.** Un `init` reescribiría `src/styles/globals.css` con sus defaults, y ahí vive la **tematización por organización** (§4.5.1): la romperías.
> Los `paths` del alias están **duplicados en el `tsconfig.json` raíz solo para el tooling**: es un tsconfig *solution* (`files: []`) y sin ellos el CLI no resuelve `@/` y escribe los componentes en una carpeta **literal `@/`**.
> **Tras cada `add`, verifica con `git diff` que no tocó `globals.css`.** `components.json` declara Tailwind 4 (`"config": ""`), así que el registro genera componentes v4 que encajan sin adaptar.

> Regla: un componente **genérico y reutilizable** (un `Badge` base, una `Table`) vive en `core/ui`, no en `modules/<cap>/`. Lo específico de un dominio (p. ej. `PatientStatusBadge`, que conoce el enum de estado del paciente) sí vive en `modules/<cap>/components/`, y puede componerse sobre el primitivo de `core/ui`.

### 4.2 Animación (Framer Motion)
> **Instalado.** Para transiciones simples que no involucran entrar/salir del árbol (hover, color,
> spinners) sigue prefiriéndose **CSS de Tailwind** (`transition-colors`, `animate-pulse`,
> `hover:`/`focus:`) — más barato, sin JS. `framer-motion` entra cuando hace falta animar
> entrada/salida del árbol o layout, no antes (mismo criterio de "instálalo cuando haga falta" con
> el que se adoptó: ver `PatientRow`/`PatientList`, primer uso real).

> [!IMPORTANT]
> **`<MotionConfig reducedMotion="user">` envuelve toda la app** (`layouts/RootLayout.tsx`): respeta
> `prefers-reduced-motion` del SO para **cualquier** `motion.*`/`AnimatePresence` sin que cada
> componente tenga que pensar en ello. No dupliques ese chequeo por componente.

- **`<AnimatePresence>`** para componentes que entran/salen del árbol (diálogos, menús colapsables,
  filas de una lista que se filtra — ver `PatientList`: `initial={false}` para no animar la carga
  inicial, solo altas/bajas posteriores).
- **`layout`/`layoutId`** para transiciones fluidas de posición/tamaño (ej. píldora de pestaña activa,
  o el reflow de una lista cuando una fila entra/sale — `layout="position"` en `PatientRow`).
- **Solo `transform`/`opacity`** (nunca `width`/`height`/`top`/`left`: repintan caro). En framer-motion,
  anima `x`/`y`/`scale`/`opacity`, no propiedades de layout CSS crudas.
- **Micro-interacciones rápidas** (0.2s–0.3s); prefiere `spring` (con `stiffness`/`damping`) sobre curvas lineales.
- **Respeta el estado colapsado**: oculta ancho/opacidad para evitar desbordes de texto.
- **Con moderación**: 1-2 elementos animados por vista como regla general; una lista que anima sus
  propias entradas/salidas al cambiar es un único movimiento coherente, no "animar todo".

### 4.3 Responsividad (Mobile-First)
- Breakpoints: **móvil** < `480px`, **tablet** `481–1024px`, **escritorio** > `1024px`.
- Prueba cambios de navegación/estilos en móvil, tablet y escritorio.

> **Estado actual (objetivo, aún no adoptado):** la Sidebar del `AppLayout` (§1) no existe todavía.
> Objetivo cuando se construya: cajón flotante en móvil, barra de iconos en mediano, expandida en
> escritorio.

### 4.4 Accesibilidad (WCAG)
- **HTML semántico** (`<aside>`, `<nav>`, `<main>`, `<header>`, `<article>`).
- **Navegación por teclado**: todo interactivo accesible con `Tab` y activable con `Enter`/`Espacio`.
- **Formularios**: cada `input` con su `label` enlazado (`htmlFor`/`id`).
- **ARIA**: `aria-label` en botones solo-icono; `aria-describedby` para textos/errores.

### 4.5 Paleta y variables de estilo (Tailwind v4 + CSS vars)
Toda la configuración de Tailwind vive en `src/styles/globals.css` (no hay `tailwind.config.*` ni PostCSS: Tailwind entra como plugin de Vite, `@tailwindcss/vite`).

- **Tokens con color completo** en `:root` (claro) y `.dark` (oscuro): `--primary: hsl(292 72% 36%)`. Es el formato que emite el registro de shadcn para v4 y el que escribe el tema de cada clínica (§4.5.1). Un valor en OKLCH (`oklch(...)`) también sirve tal cual: ya no hay envoltorio `hsl(var(...))` que lo rompa.
- **`@theme inline`** mapea cada token a su utilidad (`--color-primary: var(--primary)` → `bg-primary`, `text-primary`, `bg-primary/10`…). El `inline` hace que la utilidad lea la variable en runtime: el cambio claro/oscuro y el tema de la clínica funcionan sin recompilar; la opacidad sale con `color-mix()`.
- **Modo oscuro por clase**: `@custom-variant dark (&:where(.dark, .dark *))` — la clase `dark` en `<html>` la maneja `ThemeToggle`; nunca `prefers-color-scheme`.
- **Navegadores**: Tailwind 4 usa `color-mix()`, `@property` y capas en cascada en runtime. Requiere **Safari 16.4+, Chrome 111+, Firefox 128+**; en uno anterior la app se ve sin estilos correctos.
- **Opacidad** (`bg-primary/10`): v4 la resuelve con `color-mix(in oklab, var(--primary) 10%, transparent)` en vez de `hsl(... / 0.1)`. El resultado es el mismo color con transparencia; al migrar se midió una diferencia máxima de **1/255 por canal** (redondeo), invisible.
- **Animaciones** de los componentes de Radix (`animate-in`, `fade-in-0`, `zoom-in-95`…): `tw-animate-css`.
- **Tokens de marca del producto** (`--brand-cta`, `--brand-teal`, `--brand-hero-*`…): colores del login que **no** siguen al tema de la clínica. `deriveTheme` nunca los emite. Úsalos solo para la identidad de Dentdesk.

> [!IMPORTANT]
> **Sin colores literales en componentes** (`#hex` y funciones de color: `rgb()`, `hsl()`, `oklch()`…; no se buscan colores con nombre porque chocan con clases como `text-white`): cambiar un color se hace en `globals.css`. Lo vigila `src/styles/noLiteralColors.test.ts`, con una lista de excepciones justificadas en el propio test (colores normativos y papel del odontograma, presets de marca, el generador `deriveTheme`). Si un archivo de la lista deja de tener literales, el test pide sacarlo. El contrato del tema (utilidades desde tokens, paridad claro/oscuro, `dark:` por clase) lo compila `src/styles/themeContract.test.ts` con `@tailwindcss/node`.

#### 4.5.1 Tematización por organización (branding)
Capacidad `organization-branding`: cada organización personaliza su color de marca y logo, aplicados al **app shell** (post-login). Piezas:

- **`deriveTheme(colors)`** (`modules/core/theme`) — lógica **pura** (sin React, testeable): de 1-2 colores de marca deriva los tokens (claro + oscuro), como **color completo** (`hsl(H S% L%)`, el mismo formato que `globals.css`). Los `-foreground` se eligen por **contraste WCAG AA** (alternan solo blanco/neutro oscuro, nunca generan colores); *lightness clamping* por modo; los **neutros** conservan S/L y solo cambian el hue; los **semánticos** (éxito/aviso/error) **no** se tocan.
- **`ThemeManager`** (sin React) — inyecta las variables en un `<style>` con `:root { … }` y `.dark { … }`; `reset()` restaura los defaults del producto. **El DOM lo toca el ThemeManager, no los hooks.**
- **`useBranding`** (en `RootLayout`) — reactivo al **`organizationId` activo** (no al login): al cambiar, obtiene la marca, deriva y aplica; resetea al cerrar sesión.
- **Cache-first** (`brandingCache`, `localStorage` por organización) — pinta al instante en el arranque (`applyCachedThemeOnBoot` en `main.tsx`) y se corrige tras el refresh; limpia en logout.
- El **favicon no se personaliza** (siempre el del producto); el **logo** cae al del producto ante error (`OrgLogo`, `onError`).

> Regla: personalizar **solo el/los color(es) de marca**; nunca sobreescribir semánticos ni editar el tema token a token. La derivación garantiza legibilidad; una elección con mal contraste **avisa** en el editor pero nunca produce texto ilegible.

### 4.6 Composición: patrón "Slots" / "Children"
Cuando un componente impone **estructura** pero el **contenido** lo decide quien lo consume, compón el contenido como `children` o *slots* nombrados (`ReactNode`), no como datos en props. De mayor a menor preferencia:

1. **`children`** (slot por defecto) — el más común y legible. El padre define el marco, el hijo va dentro.
2. **Slots nombrados** (`title`, `actions`, `children`…) — cuando hay varias zonas estructurales.
3. **Compound components** (`<Tabs><Tabs.Tab/></Tabs>`) — solo cuando aporta legibilidad real frente a slots.

**SÍ** usar slots/children cuando el componente es **reutilizable** y solo aporta estructura/estilo/animación (layouts, cards, modales, page shells) y el contenido es JSX estático conocido por quien escribe el JSX.

> [!IMPORTANT]
> **NO** uses slots/children cuando el componente debe **decidir cuál de varios elementos renderizar** a partir de **datos dinámicos** o necesita metadatos por elemento (clave, etiqueta, icono, permisos): usa un API de **lista de configuración** (`items: Option[]`) o *render prop*. Ej.: un contenedor multivista recibe `views: ViewOption[]` cuando la pantalla las genera dinámicamente con `.map()`.

**Reglas:** estructura en el padre, contenido en el consumidor; no envuelvas JSX en props de datos sin necesidad; no introduzcas compound components hasta tener 2–3 casos reales (ver `docs/coding-style.md`); mantén roles/ARIA en el componente estructural, no en el contenido inyectado.

> [!NOTE]
> Las **pantallas** (`screens/**`) son hojas de composición: montan una feature en un layout. **No** son lugar para slots/children — el patrón rinde en componentes **reutilizables** de `core`. Señal de mal uso: envolver una **única** vista en un contenedor multivista con `onViewChange={() => {}}` (no-op).
