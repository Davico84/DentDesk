import { useEffect, useRef } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { AnimatePresence } from "framer-motion";
import { Search, UserPlus } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { Input } from "@/modules/core/ui/input";
import { useDebouncedValue } from "@/modules/core/hooks/useDebouncedValue";
import { PATHS } from "@/routes/paths";
import { usePatientsSearch } from "@/modules/patients/hooks/usePatientsApi";
import { PatientRow } from "./PatientRow";
import { PatientListSkeleton } from "./PatientListSkeleton";
import { PatientListEmpty } from "./PatientListEmpty";
import { PatientListError } from "./PatientListError";
import { PageContainer } from "@/modules/core/components/PageContainer";
import { rememberPatientsList, wantsSearchFocus } from "@/modules/patients/utils/listNavigation";

const PAGE_SIZE = 20;

/** 44px de alto (h-11): el mínimo táctil. Por debajo, fallar el toque en tablet es cuestión de tiempo. */
const PAGER_BUTTON =
  "h-11 cursor-pointer rounded-xl border border-input bg-card px-4 font-medium transition-colors duration-200 hover:bg-muted/50 disabled:cursor-not-allowed disabled:opacity-40";

/** Parámetros de la URL del listado (`add-app-shell` design D8). La página va 1-based: es la que se ve. */
const PARAM = { query: "q", page: "pagina", inactive: "inactivos" } as const;

/**
 * Feature del listado de pacientes: búsqueda, inclusión de inactivos, paginación y estados. Vive
 * en el módulo; la pantalla (`screens/patients/PatientListScreen`) solo la monta.
 *
 * La búsqueda, la página y los inactivos viven en la URL: recargar o volver desde la ficha
 * muestra lo mismo. Al tipear se reemplaza la entrada del historial en vez de apilar una por tecla.
 */
export function PatientList() {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const searchRef = useRef<HTMLInputElement>(null);

  const query = params.get(PARAM.query) ?? "";
  const includeInactive = params.get(PARAM.inactive) === "1";
  const page = Math.max(0, (Number.parseInt(params.get(PARAM.page) ?? "1", 10) || 1) - 1);

  /** Cambia los parámetros dados; `null` lo quita. Cambiar la búsqueda o el filtro vuelve a la página 1. */
  const update = (changes: Partial<Record<keyof typeof PARAM, string | null>>, replace = false) => {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        for (const [key, value] of Object.entries(changes) as [keyof typeof PARAM, string | null][]) {
          if (value === null || value === "") next.delete(PARAM[key]);
          else next.set(PARAM[key], value);
        }
        if (!("page" in changes)) next.delete(PARAM.page);
        return next;
      },
      { replace },
    );
  };
  const setPage = (p: number) => update({ page: p > 0 ? String(p + 1) : null });

  // Registra esta URL para los enlaces "volver al listado" de la ficha y el formulario.
  useEffect(() => {
    rememberPatientsList(`${location.pathname}${location.search}`);
  }, [location.pathname, location.search]);

  // Atajo "Buscar paciente" del Inicio: llega con el pedido de enfocar el buscador.
  const focusSearch = wantsSearchFocus(location.state);
  useEffect(() => {
    if (focusSearch) searchRef.current?.focus();
  }, [focusSearch]);

  // Se consulta cuando dejas de escribir, no en cada tecla: "Ada Lovelace" serían 12 peticiones,
  // y las respuestas podrían llegar desordenadas. Lo que se teclea sigue siendo inmediato.
  const debouncedQuery = useDebouncedValue(query, 300);

  const { data, isLoading, isError } = usePatientsSearch({
    query: debouncedQuery.trim() || undefined,
    includeInactive,
    page,
    size: PAGE_SIZE,
  });

  const patients = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 1;
  const isLast = data?.last ?? true;
  const hasResults = !isLoading && !isError && patients.length > 0;
  // Se compara con la query YA aplicada: si no, al teclear diría "nadie coincide" mientras el
  // resultado en pantalla todavía es el anterior.
  const searching = debouncedQuery.trim().length > 0;

  return (
    <PageContainer>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Pacientes</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {total > 0
              ? `${total} ${total === 1 ? "persona registrada" : "personas registradas"} en tu clínica`
              : "El corazón de tu clínica"}
          </p>
        </div>
        <Link to={PATHS.PATIENT_NEW} className={buttonVariants({ variant: "default" })}>
          <UserPlus className="mr-2 size-4" />
          Nuevo paciente
        </Link>
      </header>

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-[220px] flex-1">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            type="search"
            className="h-11 rounded-xl pl-10"
            placeholder="Buscar por nombre o documento…"
            ref={searchRef}
            value={query}
            onChange={(e) => update({ query: e.target.value }, true)}
            aria-label="Buscar pacientes"
          />
        </div>
        <button
          type="button"
          aria-pressed={includeInactive}
          onClick={() => update({ inactive: includeInactive ? null : "1" })}
          className={[
            "h-11 cursor-pointer rounded-xl border px-4 text-sm font-medium transition-colors duration-200",
            includeInactive
              ? "border-primary/30 bg-primary/10 text-primary"
              : "border-input bg-card text-muted-foreground hover:bg-muted/50",
          ].join(" ")}
        >
          Incluir dados de baja
        </button>
      </div>

      <div className="overflow-hidden rounded-2xl border bg-card shadow-xs">
        {isLoading ? (
          <PatientListSkeleton />
        ) : isError ? (
          <PatientListError />
        ) : patients.length === 0 ? (
          <PatientListEmpty hasQuery={searching} />
        ) : (
          <ul className="divide-y">
            {/* initial={false}: no animar la carga inicial de la página, solo altas/bajas
                posteriores (filtro, paginación) — evita un parpadeo de entrada en cada visita. */}
            <AnimatePresence initial={false}>
              {patients.map((p) => (
                <PatientRow key={p.id} patient={p} />
              ))}
            </AnimatePresence>
          </ul>
        )}
      </div>

      {hasResults ? (
        <footer className="flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground">
          <span>
            Página {page + 1} de {totalPages} · {total} en total
          </span>
          {/* Botones de 44px: por debajo, fallar el toque en tablet es cuestión de tiempo. */}
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage(Math.max(0, page - 1))}
              className={PAGER_BUTTON}
            >
              Anterior
            </button>
            <button type="button" disabled={isLast} onClick={() => setPage(page + 1)} className={PAGER_BUTTON}>
              Siguiente
            </button>
          </div>
        </footer>
      ) : null}
    </PageContainer>
  );
}
