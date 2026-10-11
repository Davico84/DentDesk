/** Variables de entorno que deciden la URL base de la API. */
interface ApiEnv {
  VITE_API_URL?: string;
  DEV: boolean;
}

/**
 * URL base de la API (`add-cloud-deployment` D4). Con `VITE_API_URL` se usa ese valor. Sin ella:
 * en desarrollo, el backend local bajo su base (`http://localhost:8080/api`); en producción, `/api`
 * en el **mismo origen** (Vercel reenvía `/api/*` al backend), así la cookie de refresh es de
 * primera parte. Los paths del cliente generado son relativos a esta base. Ver docs/deployment.md.
 */
export function resolveApiBaseUrl({ VITE_API_URL, DEV }: ApiEnv): string {
  if (VITE_API_URL !== undefined && VITE_API_URL !== "") return VITE_API_URL;
  return DEV ? "http://localhost:8080/api" : "/api";
}
