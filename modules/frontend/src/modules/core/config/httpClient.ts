import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";
import type { AuthResponse } from "@/modules/core/services/generated/model";
import { useSessionStore } from "@/store/useSessionStore";
import { useSlowRequestStore } from "@/store/useSlowRequestStore";
import { WARMING_AFTER_S } from "@/modules/core/utils/serverWarmup";
import { resolveApiBaseUrl } from "./apiBaseUrl";

const BASE_URL = resolveApiBaseUrl(import.meta.env);

/** Instancia axios compartida. `withCredentials` para enviar la cookie de refresh. */
export const axiosInstance = axios.create({
  baseURL: BASE_URL,
  withCredentials: true,
});

/** Inyecta el access token (en memoria) en cada petición. */
axiosInstance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = useSessionStore.getState().accessToken;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// --- Peticiones lentas (update-slow-request-notice D1) ---
// Cada petición arranca un timer; si vence antes de la respuesta, se marca lenta y la app muestra
// "Despertando el servidor…". La respuesta (éxito o error) cancela el timer y la quita. Un reintento
// tras un 401 es otra petición: tiene su propio id y su propio timer.

type TrackedConfig = InternalAxiosRequestConfig & { _slowId?: number; _slowTimer?: ReturnType<typeof setTimeout> };

let nextRequestId = 0;

axiosInstance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const tracked = config as TrackedConfig;
  const id = ++nextRequestId;
  tracked._slowId = id;
  tracked._slowTimer = setTimeout(
    () => useSlowRequestStore.getState().markSlow(id, Date.now()),
    WARMING_AFTER_S * 1000,
  );
  return config;
});

function settle(config: InternalAxiosRequestConfig | undefined) {
  const tracked = config as TrackedConfig | undefined;
  if (tracked?._slowId === undefined) return;
  clearTimeout(tracked._slowTimer);
  useSlowRequestStore.getState().done(tracked._slowId);
}

axiosInstance.interceptors.response.use(
  (response) => {
    settle(response.config);
    return response;
  },
  (error: AxiosError) => {
    settle(error.config);
    return Promise.reject(error);
  },
);

// --- Refresco transparente ante 401 (single-flight) ---

type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

let refreshPromise: Promise<string> | null = null;

function isAuthPath(url?: string): boolean {
  return !!url && (url.includes("/auth/login") || url.includes("/auth/register") || url.includes("/auth/refresh"));
}

async function doRefresh(): Promise<string> {
  const { data } = await axiosInstance.post<AuthResponse>("/auth/refresh");
  useSessionStore.getState().setSession(data.accessToken, data.user);
  return data.accessToken;
}

/**
 * Refresca la sesión con **single-flight**: llamadas concurrentes (bootstrap al
 * recargar, StrictMode, varias peticiones con 401 a la vez) comparten una única
 * petición `/auth/refresh` en vuelo. Evita rotaciones concurrentes del refresh
 * token (que en el backend causaban un 500 por consumir la misma fila dos veces).
 */
export function refreshSession(): Promise<string> {
  refreshPromise ??= doRefresh().finally(() => {
    refreshPromise = null;
  });
  return refreshPromise;
}

axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status;

    if (status === 401 && original && !original._retry && !isAuthPath(original.url)) {
      original._retry = true;
      try {
        const newToken = await refreshSession();
        original.headers.Authorization = `Bearer ${newToken}`;
        return axiosInstance(original);
      } catch (refreshError) {
        useSessionStore.getState().clear(); // el RouteGuard redirige a login
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  },
);

/** Mutator que consume el cliente generado por orval. */
export const customInstance = <T>(config: AxiosRequestConfig): Promise<T> =>
  axiosInstance({ ...config }).then(({ data }) => data as T);

export default customInstance;
