# React Query & API Layer

> Parte de la documentación de OdontoRisas. Complementa a [architecture.md](./architecture.md) y [testing.md](./testing.md).

Este documento define cómo interactuar con el backend y cómo gestionar el estado del servidor utilizando **TanStack Query (React Query)** y **Axios**.

---

## 1. Reglas Generales del Estado del Servidor

* **Estado Único**: Todos los datos que provengan del backend (estado del servidor) pertenecen a React Query. No se deben clonar ni duplicar estos datos en Zustand.
* **Control de Estados**: Todos los componentes que consuman datos del servidor deben prever y manejar explícitamente:
  * Estado de carga (`isLoading` / `isPending`).
  * Estado de error (`error` / `isError`).
  * Estado vacío (`empty`) cuando la respuesta esté vacía.

---

## 2. Convención de Query Keys

Para mantener un control eficiente del cache e invalidaciones organizadas, cada dominio/módulo debe implementar un objeto factoría de Query Keys en sus hooks.

### Estructura base de una factoría
```typescript
export const userKeys = {
  all: ["user"] as const,
  simpleProfile: () => [...userKeys.all, "simple-profile"] as const,
  fullProfile: () => [...userKeys.all, "full-profile"] as const,
};
```

### Reglas:
1. **Clave Raíz (`all`)**: Debe ser única por módulo/dominio y expresada como un array de un solo elemento `["nombre-modulo"] as const`.
2. **Subclaves paramétricas/específicas**: Se definen como funciones que extienden el array raíz mediante desestructuración para mantener la jerarquía de cache.
3. **Invalidación Coherente**: Al invalidar mediante `queryClient.invalidateQueries({ queryKey: userKeys.all })`, se invalidarán y refrescarán automáticamente tanto el perfil simple como el completo.

---

## 3. Estructura de la Capa de API

El flujo de consumo HTTP debe estar completamente desacoplado de las vistas de React:

```
Componente/Pantalla (React) ──> Custom Hook (React Query) ──> Servicio (Axios) ──> Enrutador API
```

### Reglas:
* **Servicios Encapsulados**: Las llamadas HTTP directas con Axios se realizan únicamente dentro de archivos de servicio (ej. `frontend/src/modules/users/services/user.ts`).
* **Componentes Limpios**: Ningún componente o pantalla de React debe importar `axios` o llamar a `apiSecure`/`api` directamente.
* **Tipado de Contratos**: Todas las peticiones y respuestas HTTP deben estar tipadas en interfaces explícitas (ej. `CreateTagRequest`, `UserPreview`).

---

## 4. Ejemplo Práctico de Implementación

### 1. Definición del Endpoint

Los endpoints se centralizan en el módulo core, siguiendo la convención `frontend/src/modules/core/services/index.ts`:
```typescript
export const Services = {
  users: {
    me: "/users/me",
  }
};
```

### 2. El Servicio

Cada dominio expone su servicio Axios, siguiendo la convención `frontend/src/modules/users/services/user.ts`:
```typescript
import { apiSecure } from "@/config/httpClient";
import { Services } from "@/modules/core/services";
import type { UserProfile } from "../types";

export const UserService = {
  async getUserProfile(): Promise<UserProfile> {
    const response = await apiSecure.get<UserProfile>(Services.users.me);
    return response.data;
  },
};
```

### 3. El Custom Hook

El hook de React Query vive junto a su módulo, siguiendo la convención `frontend/src/modules/users/hooks/useUserProfile.ts`:
```typescript
import { useQuery, type UseQueryOptions } from "@tanstack/react-query";
import { UserService } from "../services/user";
import type { UserProfile } from "../types";

export const userKeys = {
  all: ["user"] as const,
  fullProfile: () => [...userKeys.all, "full-profile"] as const,
};

export function useUserProfile(
  options?: Omit<UseQueryOptions<UserProfile, Error>, "queryKey" | "queryFn">
) {
  return useQuery<UserProfile, Error>({
    queryKey: userKeys.fullProfile(),
    queryFn: () => UserService.getUserProfile(),
    ...options,
  });
}
```

### 4. Uso en Componente
```tsx
const { data: profile, isLoading, isError } = useUserProfile();

if (isLoading) return <LoadingSpinner />;
if (isError) return <ErrorMessage />;
return <div>Hola, {profile.username}</div>;
```

---

## 5. Validación de respuestas en el *boundary* (Zod)

> [!IMPORTANT]
> El tipado de TypeScript es solo en compilación; la respuesta del backend es
> dinámica en runtime. Si el backend renombra/elimina un campo, el tipo "miente"
> y revienta más tarde en la UI. Por eso **validamos la respuesta con Zod en el
> `queryFn`** (el punto donde los datos entran a la app).

### Patrón
1. Define un **schema Zod** del DTO (junto a los tipos del módulo, en `schemas/`).
   Valida **estrictamente lo que la UI consume**; sé lenient con metadata que no
   usas (`z.object` ignora/strippea claves desconocidas, no falla por ellas).
2. En el `queryFn`, valida con el helper `parseApiResponse`, ubicado según la
   convención en `frontend/src/modules/core/utils/validateApiResponse.ts`:
   si el shape no calza, lanza un error controlado → React Query lo expone como
   `isError` (estado manejado) en vez de un crash silencioso.

```typescript
queryFn: async () => {
  const data = await ContactsService.listContacts(params);
  // Valida el shape en el boundary; devolvemos el DTO ya tipado del service.
  parseApiResponse(contactsResponseSchema, data, "contacts.list");
  return data;
},
```

### Reglas
- **Solo en lecturas que la UI renderiza** (lo crítico primero); no hace falta
  validar cada endpoint de golpe, se va adoptando incrementalmente.
- **No sobre-valides el envelope** (p. ej. metadata de paginación): valida
  `content`/los items y deja pasar el resto.
- Referencia: `schemas/contact.ts` + `useContacts` (boundary), test del helper en
  `validateApiResponse.test.ts`.
- A futuro, lo ideal es **inferir los tipos desde los schemas** (`z.infer`) para
  tener una única fuente de verdad y evitar drift entre interface y schema.
