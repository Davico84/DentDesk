# Estado del Cliente (Zustand)

> Parte de la documentación de OdontoRisas. Complementa a [architecture.md](./architecture.md) y [testing.md](./testing.md).

Este documento define las directrices y estándares para la gestión del estado global del lado del cliente utilizando **Zustand**.

---

## 1. Cuándo usar Zustand

Zustand se utiliza exclusivamente para almacenar **estado del cliente global**. Esto incluye datos efímeros que no persisten en base de datos de manera síncrona o configuraciones de la interfaz de usuario que necesitan compartirse entre múltiples componentes independientes.

### Ejemplos válidos de uso:
* Filtros de tablas activos (`useFiltersStore`).
* Dirección de ordenamiento y campos seleccionados (`useSortingStore`).
* Datos del usuario de sesión guardados localmente para evitar parpadeos de carga (`useUserStore`).
* Selección temporal de países o configuraciones regionales en memoria.

### Cuándo NO usar Zustand:
* **No duplicar datos de API**: No crees stores de Zustand para almacenar listas de usuarios, tags, detalles de contactos, etc. Ese tipo de estado es del servidor y debe ser gestionado, cacheado e invalidado mediante **React Query**.
* **Evitar estado redundante**: Si un dato puede ser calculado directamente (ej. contar elementos activos en una lista filtrada), calcúlalo en tiempo de renderizado o mediante `useMemo`, no lo guardes como una variable extra en el store.

---

## 2. Convención y Estructura de Stores

Todos los stores se agrupan en la carpeta `frontend/src/store/` y deben seguir la siguiente estructura estandarizada:

### Estructura de Archivo
1. **Definición de la Interfaz (`[Feature]State`)**: Define explícitamente el tipado de todos los valores del estado y de las funciones de acción.
2. **Creación del Store**: Se inicializa utilizando el helper `create` de Zustand indicando la interfaz de estado en el genérico.
3. **Exportación Única**: Se exporta el hook con la nomenclatura `use[Feature]Store`, **envuelto en `createSelectors`** para exponer selectores atómicos (`store.use.<campo>()`). Ver sección 3.

### Ejemplo Práctico

Siguiendo la convención `frontend/src/store/useSortingStore.ts`:
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

// Exportación única envuelta en createSelectors (ver sección 3).
export const useSortingStore = createSelectors(useSortingStoreBase);
```

---

## 3. Buenas Prácticas de Rendimiento

> [!IMPORTANT]
> **Regla no-negociable: nunca se consume el store completo, siempre por selector.**
> Hacer `const { a, b } = useStore()` suscribe el componente a *cualquier* cambio
> del store y provoca renders innecesarios.

* **Patrón estándar — `createSelectors`**: Envuelve cada store con el helper
  `createSelectors` (ubicado según la convención en `frontend/src/store/createSelectors.ts`)
  al exportarlo. Genera selectores atómicos accesibles como `store.use.<campo>()`,
  de modo que el acceso correcto (por selector) es el camino por defecto y no hay
  que escribir un hook por campo. Es la forma recomendada de exponer y consumir stores.
  ```tsx
  // store
  export const useUserStore = createSelectors(create<UserState>()(...));

  // consumo: cada llamada es un selector atómico (re-render solo si ese campo cambia)
  const user = useUserStore.use.user();
  const setUser = useUserStore.use.setUser();
  ```
* **Uso de Selectores**: Al consumir el store, siempre utiliza selectores específicos para evitar renders innecesarios en tus componentes.
  ```tsx
  // RECOMENDADO: Solo re-renderiza si 'sortBy' cambia
  const sortBy = useSortingStore((state) => state.sortBy);
  const setSortBy = useSortingStore((state) => state.setSortBy);

  // EVITAR: El componente se re-renderizará ante CUALQUIER cambio del store
  const { sortBy, setSortBy } = useSortingStore();
  ```
* **Selección de varios valores con `useShallow`**: Cuando necesites varios campos del store en un mismo objeto, envuelve el selector en `useShallow` (de `zustand/react/shallow`) para comparar por igualdad superficial y evitar renders por una referencia nueva en cada render.
  ```tsx
  import { useShallow } from "zustand/react/shallow";

  const { sortBy, orderBy } = useSortingStore(
    useShallow((state) => ({ sortBy: state.sortBy, orderBy: state.orderBy }))
  );
  ```
* **Múltiples Stores Pequeños**: Es preferible tener varios stores pequeños y enfocados (ej. uno para ordenamiento, uno para filtros, uno para sesión) en lugar de un único store gigante monolítico.
* **Persistencia Limpia**: Si necesitas persistir datos en `localStorage`, hazlo de manera controlada y segura dentro de las acciones del store, controlando posibles errores de parseo JSON.

---

## 4. Separación de Responsabilidades

* **Un store = estado + acciones, nada más.** El store guarda estado de cliente y
  las acciones que lo mutan. **No** debe contener lógica de negocio, ni valores
  derivados (calcúlalos en el render o con `useMemo`), ni estado del servidor.
* **Preferir el middleware `persist` sobre `localStorage` manual.** Para persistir,
  usa `persist` (de `zustand/middleware`) en lugar de llamar a `localStorage` dentro
  de cada acción. Es declarativo, hidrata el estado al iniciar y centraliza el
  manejo de errores de serialización.
  ```typescript
  import { create } from "zustand";
  import { persist } from "zustand/middleware";
  import { createSelectors } from "./createSelectors";

  const useUserStoreBase = create<UserState>()(
    persist(
      (set) => ({
        user: null,
        setUser: (user) => set({ user }),
        clearUser: () => set({ user: null }),
      }),
      { name: "user" } // clave en localStorage
    )
  );

  export const useUserStore = createSelectors(useUserStoreBase);
  ```
* **La lógica de orquestación vive en hooks, no en componentes.** Si necesitas
  combinar el store con queries, efectos o reglas, hazlo en un custom hook (patrón
  "componente tonto + hook inteligente").
