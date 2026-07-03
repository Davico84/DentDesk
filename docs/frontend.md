# Estándares de Frontend — OdontoRisas

Dimensión **tecnológica** (reutilizable). Guía única del cliente: stack, arquitectura de pantallas/módulos, capa de datos (React Query + Axios), estado de cliente (Zustand) y patrones de UI. El **estilo general de código** (TypeScript, nombres, legibilidad) vive en `docs/coding-style.md`; el **stack cerrado y versiones** en `docs/architecture.md §3`; las **pruebas** en `docs/testing.md`.

---

## 1. Stack y arquitectura de pantallas/módulos

**Stack** (detalle y versiones en `docs/architecture.md §3`): React 19 · TypeScript · Vite. UI: Tailwind + shadcn/Radix + `@base-ui/react`, `lucide-react`, `framer-motion`, `sonner`, `recharts`. Datos de servidor: `@tanstack/react-query` + `axios`. Estado de cliente: Zustand. Formularios: `react-hook-form` + `zod`. Routing: `react-router-dom` v7 con `RouteGuard` central (roles `ADMIN`/`USER`/`ALL`). Realtime: `@stomp/stompjs` + `sockjs-client`. Gestor: pnpm.

**Estructura** (`modules/frontend/src/`):

```
src/
  layouts/     # marcos de página (shells) reutilizables
  screens/     # pantallas: orquestan una feature dentro de un layout (hojas de composición)
  modules/     # lógica encapsulada por dominio
    core/      # transversal: ui (shadcn), components, services, utils, enum — SIN deps a otros módulos
    <cap>/     # un módulo por capacidad (hooks de dominio, components, types, schemas Zod)
  store/       # stores Zustand (estado de cliente)
  routes/      # definición de rutas + RouteGuard
  styles/      # globals.css y variables de tema
```

**Reglas de arquitectura:**
- **`screens` orquestan; `modules` encapsulan lógica.** Las pantallas montan features dentro de un layout; no contienen lógica de negocio.
- **`core` no depende de otros módulos** (es la base transversal: UI, auth guard, utils). Los demás módulos pueden depender de `core`.
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

### 2.3 Estructura de la capa de API (generada con orval)

El cliente HTTP se **genera** desde el contrato OpenAPI del backend (orval); **no** se escriben clientes axios a mano por módulo. Flujo:

```
Pantalla ──> hook de dominio (modules/<cap>/hooks) ──> hook generado (orval) ──> mutator axios (core) ──> API
```

- **Cliente generado (core)**: `core/services/generated/` contiene los **tipos DTO** del servidor (en `generated/model/`) y los **hooks base** de react-query (`useLogin`, `useRegister`…), producidos por orval desde `contracts/openapi.json`. Es código de máquina: **versionado pero no se edita a mano** (se regenera con `pnpm generate:api`). Excluido de ESLint; se valida con `tsc`.
- **HTTP base (core, a mano)**: `core/services/httpClient.ts` — la instancia axios (`withCredentials`, inyección de `Bearer`, interceptor de refresh) que orval usa como *mutator*.
- **Lógica de dominio (módulo)**: `modules/<cap>/` **envuelve** los hooks generados con lo propio de la capacidad: `schemas/` (Zod de formularios), `hooks/` de orquestación (combinan el hook generado con sesión, redirect, toasts), `components/`, `types/` propios. No hay cliente axios por módulo.
- **Componentes limpios**: ninguna pantalla importa `axios` directamente; usa hooks de dominio (o, para un caso trivial, el hook generado).
- **Contratos tipados**: los tipos request/response salen del contrato (orval), **nunca se teclean a mano**. Ver `api-type-contracts` y `docs/coding-style.md §7` (paridad de validación).

### 2.4 Ejemplo (hook generado → hook de dominio → pantalla)

```typescript
// modules/auth/hooks/useLoginFlow.ts — orquestación de dominio (envuelve el hook generado)
import { useLogin } from "@/modules/core/services/generated/api";
import { useSessionStore } from "@/store/useSessionStore";

export function useLoginFlow() {
  const setSession = useSessionStore((s) => s.setSession);
  return useLogin({
    mutation: {
      // el shape de la respuesta viene del contrato (AuthResponse generado)
      onSuccess: (auth) => setSession(auth.accessToken, auth.user),
    },
  });
}
```
```tsx
// uso en la pantalla
const { mutate: login, isPending, isError } = useLoginFlow();
// login({ data: { email, password } });
```

> Para endpoints GET, orval genera además la **query key** y el hook `useQuery`; la convención de §2.2 aplica a cualquier hook de dominio que compongas sobre ellos.

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

### 4.1 shadcn/ui e integración
> [!IMPORTANT]
> **Antes de crear un componente visual o añadir una librería**, revisa si ya existe en `modules/core/ui/`. Reutilizar garantiza temas (claro/oscuro), overrides de marca, estilos consistentes (bordes, sombras, transiciones, accesibilidad) y evita duplicación y bundle innecesario.

> [!IMPORTANT]
> **Nunca uses el atributo nativo `title`** en un elemento interactivo: usa el componente `Tooltip` (`modules/core/ui/tooltip.tsx`). El `Button` de core/ui acepta prop `tooltip`. Mantén siempre `aria-label` (el tooltip no lo sustituye).

Componentes base en `modules/core/ui/`: `Dialog`, `Sheet`, `Popover`, `DropdownMenu`, `Select`, `Tooltip`, `Table`, `Badge`, etc.

### 4.2 Animación (Framer Motion)
- **`<AnimatePresence>`** para componentes que entran/salen del árbol (diálogos, menús colapsables).
- **`layout`/`layoutId`** para transiciones fluidas de posición/tamaño (ej. píldora de pestaña activa).
- **Micro-interacciones rápidas** (0.2s–0.3s); prefiere `spring` (con `stiffness`/`damping`) sobre curvas lineales.
- **Respeta el estado colapsado**: oculta ancho/opacidad para evitar desbordes de texto.

### 4.3 Responsividad (Mobile-First)
- Breakpoints: **móvil** < `480px`, **tablet** `481–1024px`, **escritorio** > `1024px`.
- Prueba cambios de navegación/Sidebar/estilos en móvil, tablet y ancho. La Sidebar: cajón flotante en móvil, barra de iconos en mediano, expandida en escritorio.

### 4.4 Accesibilidad (WCAG)
- **HTML semántico** (`<aside>`, `<nav>`, `<main>`, `<header>`, `<article>`).
- **Navegación por teclado**: todo interactivo accesible con `Tab` y activable con `Enter`/`Espacio`.
- **Formularios**: cada `input` con su `label` enlazado (`htmlFor`/`id`).
- **ARIA**: `aria-label` en botones solo-icono; `aria-describedby` para textos/errores.

### 4.5 Paleta y variables de estilo (Tailwind v3 + CSS vars)
Colores resueltos por variables CSS en `src/styles/globals.css`.
> [!WARNING]
> `tailwind.config.cjs` mapea colores con HSL: `background: "hsl(var(--background))"`. Las variables CSS deben contener **solo los 3 canales separados por espacios** (`--background: 220 25% 98%`), sin envoltorio. Si importas variables de diseños Tailwind v4/OKLCH (`oklch(...)`), **conviértelas a HSL puro** antes de pegarlas, o compilará a `hsl(oklch(...))` y romperá los estilos.

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
