import { PATHS } from "@/routes/paths";

/**
 * Volver al listado sin perder la búsqueda (`add-app-shell` design D8). El listado guarda su URL
 * (con `q`, `pagina` e `inactivos`) cada vez que se muestra; los enlaces "volver al listado" la
 * leen. `sessionStorage` es por pestaña: dos pestañas no se pisan. El logout la borra.
 */
const STORAGE_KEY = "odr-patients-list";

/** Estado de navegación que pide enfocar el buscador del listado al llegar (atajo del Inicio). */
export const FOCUS_SEARCH_STATE = { focusSearch: true } as const;

export function wantsSearchFocus(state: unknown): boolean {
  return typeof state === "object" && state !== null && (state as { focusSearch?: unknown }).focusSearch === true;
}

function isListUrl(url: string): boolean {
  return url === PATHS.PATIENTS || url.startsWith(`${PATHS.PATIENTS}?`);
}

export function rememberPatientsList(url: string): void {
  try {
    sessionStorage.setItem(STORAGE_KEY, url);
  } catch {
    /* almacenamiento no disponible: "volver" cae al listado sin filtros */
  }
}

/** La última URL del listado en esta pestaña, o el listado sin filtros. */
export function patientsListPath(): string {
  try {
    const saved = sessionStorage.getItem(STORAGE_KEY);
    if (saved && isListUrl(saved)) return saved;
  } catch {
    /* sin almacenamiento: listado sin filtros */
  }
  return PATHS.PATIENTS;
}
