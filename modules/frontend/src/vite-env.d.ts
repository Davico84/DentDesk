/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** URL base de la API. Opcional: sin ella, core/config/apiBaseUrl.ts decide por entorno. */
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
