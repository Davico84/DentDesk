import axios, {
  type AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from "axios";
import { useSessionStore, type SessionUser } from "@/store/useSessionStore";

const BASE_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8080";

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

// --- Refresco transparente ante 401 (single-flight) ---

type AuthPayload = { accessToken: string; user: SessionUser };
type RetriableConfig = InternalAxiosRequestConfig & { _retry?: boolean };

let refreshPromise: Promise<string> | null = null;

function isAuthPath(url?: string): boolean {
  return !!url && (url.includes("/auth/login") || url.includes("/auth/register") || url.includes("/auth/refresh"));
}

async function refreshSession(): Promise<string> {
  const { data } = await axiosInstance.post<AuthPayload>("/auth/refresh");
  useSessionStore.getState().setSession(data.accessToken, data.user);
  return data.accessToken;
}

axiosInstance.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined;
    const status = error.response?.status;

    if (status === 401 && original && !original._retry && !isAuthPath(original.url)) {
      original._retry = true;
      try {
        // Un solo refresh concurrente; las demás peticiones esperan el mismo.
        refreshPromise ??= refreshSession().finally(() => {
          refreshPromise = null;
        });
        const newToken = await refreshPromise;
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
