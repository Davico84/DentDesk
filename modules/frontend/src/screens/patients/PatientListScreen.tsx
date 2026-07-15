import { useState } from "react";
import { Link } from "react-router-dom";
import { ChevronRight, Search, UserPlus, Users } from "lucide-react";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { Input } from "@/modules/core/ui/input";
import { PATHS, patientDetailPath } from "@/routes/paths";
import { usePatientsSearch } from "@/modules/patients/hooks/usePatientsApi";
import { PatientAvatar } from "@/modules/patients/components/PatientAvatar";
import { StatusBadge, MinorBadge } from "@/modules/patients/components/PatientBadges";
import { ageLabel } from "@/modules/patients/components/patientDisplay";

const PAGE_SIZE = 20;

/** Listado con búsqueda de pacientes: el uso diario de recepción. */
export function PatientListScreen() {
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
          <SkeletonRows />
        ) : isError ? (
          <ErrorState />
        ) : patients.length === 0 ? (
          <EmptyState hasQuery={query.trim().length > 0} />
        ) : (
          <ul className="divide-y">
            {patients.map((p) => (
              <li key={p.id}>
                <Link
                  to={patientDetailPath(p.id!)}
                  className="group flex items-center gap-4 px-4 py-3 transition-colors hover:bg-muted/40"
                >
                  <PatientAvatar fullName={p.fullName!} muted={p.status === "INACTIVE"} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <span className="truncate font-medium">{p.fullName}</span>
                      {p.minor ? <MinorBadge /> : null}
                    </div>
                    <span className="text-xs text-muted-foreground">
                      {p.document ? `Doc. ${p.document}` : "Sin documento"}
                    </span>
                  </div>
                  <span className="hidden w-24 text-sm text-muted-foreground sm:block">{ageLabel(p.birthDate!)}</span>
                  <span className="hidden w-36 truncate text-sm text-muted-foreground md:block">{p.phone ?? "—"}</span>
                  <StatusBadge status={p.status} />
                  <ChevronRight className="size-4 shrink-0 text-muted-foreground/50 transition-transform group-hover:translate-x-0.5" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      {!isLoading && !isError && patients.length > 0 ? (
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

function SkeletonRows() {
  return (
    <ul className="divide-y">
      {Array.from({ length: 6 }).map((_, i) => (
        <li key={i} className="flex items-center gap-4 px-4 py-3">
          <div className="size-10 shrink-0 animate-pulse rounded-full bg-muted" />
          <div className="flex-1 space-y-2">
            <div className="h-3.5 w-40 animate-pulse rounded bg-muted" />
            <div className="h-2.5 w-24 animate-pulse rounded bg-muted" />
          </div>
          <div className="h-5 w-16 animate-pulse rounded-full bg-muted" />
        </li>
      ))}
    </ul>
  );
}

function EmptyState({ hasQuery }: { hasQuery: boolean }) {
  return (
    <div className="flex flex-col items-center gap-4 px-6 py-16 text-center">
      <span className="flex size-14 items-center justify-center rounded-full bg-primary/5 text-primary">
        <Users className="size-7" />
      </span>
      {hasQuery ? (
        <div className="space-y-1">
          <p className="font-medium">Nadie coincide con tu búsqueda</p>
          <p className="text-sm text-muted-foreground">Prueba con otro nombre o número de documento.</p>
        </div>
      ) : (
        <div className="space-y-1">
          <p className="font-medium">Aún no hay pacientes</p>
          <p className="text-sm text-muted-foreground">Cuando registres al primero, aparecerá aquí.</p>
        </div>
      )}
      {!hasQuery ? (
        <Link to={PATHS.PATIENT_NEW} className={buttonVariants({ variant: "default" })}>
          <UserPlus className="mr-2 size-4" />
          Registrar al primero
        </Link>
      ) : null}
    </div>
  );
}

function ErrorState() {
  return (
    <div className="px-6 py-16 text-center">
      <p className="font-medium text-destructive">No pudimos cargar los pacientes</p>
      <p className="mt-1 text-sm text-muted-foreground">Revisa tu conexión e inténtalo de nuevo en un momento.</p>
    </div>
  );
}
