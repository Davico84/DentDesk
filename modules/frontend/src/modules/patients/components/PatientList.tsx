import { useState } from "react";
import { Link } from "react-router-dom";
import { Search, UserPlus } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { Input } from "@/modules/core/ui/input";
import { PATHS } from "@/routes/paths";
import { usePatientsSearch } from "@/modules/patients/hooks/usePatientsApi";
import { PatientRow } from "./PatientRow";
import { PatientListSkeleton } from "./PatientListSkeleton";
import { PatientListEmpty, PatientListError } from "./PatientListStates";

const PAGE_SIZE = 20;

/**
 * Feature del listado de pacientes: búsqueda, inclusión de inactivos, paginación y estados. Vive
 * en el módulo; la pantalla (`screens/patients/PatientListScreen`) solo la monta.
 */
export function PatientList() {
  const [query, setQuery] = useState("");
  const [includeInactive, setIncludeInactive] = useState(false);
  const [page, setPage] = useState(0);

  const { data, isLoading, isError } = usePatientsSearch({
    query: query.trim() || undefined,
    includeInactive,
    page,
    size: PAGE_SIZE,
  });

  const patients = data?.content ?? [];
  const total = data?.totalElements ?? 0;
  const isLast = data?.last ?? true;
  const hasResults = !isLoading && !isError && patients.length > 0;

  return (
    <main className="mx-auto flex min-h-screen w-full max-w-4xl flex-col gap-6 p-6">
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
            className="h-11 rounded-xl pl-10"
            placeholder="Buscar por nombre o documento…"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setPage(0);
            }}
            aria-label="Buscar pacientes"
          />
        </div>
        <button
          type="button"
          aria-pressed={includeInactive}
          onClick={() => {
            setIncludeInactive((v) => !v);
            setPage(0);
          }}
          className={[
            "h-11 rounded-xl border px-4 text-sm font-medium transition-colors",
            includeInactive
              ? "border-primary/30 bg-primary/10 text-primary"
              : "border-input bg-card text-muted-foreground hover:bg-muted/50",
          ].join(" ")}
        >
          Incluir dados de baja
        </button>
      </div>

      <div className="overflow-hidden rounded-2xl border bg-card shadow-sm">
        {isLoading ? (
          <PatientListSkeleton />
        ) : isError ? (
          <PatientListError />
        ) : patients.length === 0 ? (
          <PatientListEmpty hasQuery={query.trim().length > 0} />
        ) : (
          <ul className="divide-y">
            {patients.map((p) => (
              <PatientRow key={p.id} patient={p} />
            ))}
          </ul>
        )}
      </div>

      {hasResults ? (
        <footer className="flex items-center justify-between text-sm text-muted-foreground">
          <span>
            Página {page + 1} · {total} en total
          </span>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              className="rounded-lg border border-input bg-card px-3 py-1.5 font-medium transition-colors hover:bg-muted/50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Anterior
            </button>
            <button
              type="button"
              disabled={isLast}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-lg border border-input bg-card px-3 py-1.5 font-medium transition-colors hover:bg-muted/50 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Siguiente
            </button>
          </div>
        </footer>
      ) : null}
    </main>
  );
}
