import { useState } from "react";
import { Link } from "react-router-dom";
import { Loader2, Plus, Search, UserPlus } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { Input } from "@/modules/core/ui/input";
import { PATHS, patientDetailPath } from "@/routes/paths";
import { usePatientsSearch } from "@/modules/patients/hooks/usePatientsApi";

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
    <main className="mx-auto flex min-h-screen max-w-4xl flex-col gap-6 p-6">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-xl font-bold">Pacientes</h1>
        <Link to={PATHS.PATIENT_NEW} className={buttonVariants({ variant: "default" })}>
          <UserPlus className="mr-2 size-4" />
          Nuevo paciente
        </Link>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="Buscar por nombre o documento"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setPage(0);
            }}
            aria-label="Buscar pacientes"
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input
            type="checkbox"
            checked={includeInactive}
            onChange={(e) => {
              setIncludeInactive(e.target.checked);
              setPage(0);
            }}
          />
          Incluir dados de baja
        </label>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-12">
          <Loader2 className="size-6 animate-spin text-muted-foreground" />
        </div>
      ) : isError ? (
        <p className="py-12 text-center text-sm text-destructive">No se pudieron cargar los pacientes.</p>
      ) : patients.length === 0 ? (
        <EmptyState hasQuery={query.trim().length > 0} />
      ) : (
        <div className="overflow-x-auto rounded-md border">
          <table className="w-full text-sm">
            <thead className="border-b bg-muted/50 text-left text-muted-foreground">
              <tr>
                <th className="px-4 py-2 font-medium">Nombre</th>
                <th className="px-4 py-2 font-medium">Documento</th>
                <th className="px-4 py-2 font-medium">Nacimiento</th>
                <th className="px-4 py-2 font-medium">Teléfono</th>
                <th className="px-4 py-2 font-medium">Estado</th>
              </tr>
            </thead>
            <tbody>
              {patients.map((p) => (
                <tr key={p.id} className="border-b last:border-0 hover:bg-muted/30">
                  <td className="px-4 py-2">
                    <Link to={patientDetailPath(p.id!)} className="font-medium text-primary hover:underline">
                      {p.fullName}
                    </Link>
                    {p.minor ? <span className="ml-2 rounded bg-accent px-1.5 py-0.5 text-xs text-accent-foreground">menor</span> : null}
                  </td>
                  <td className="px-4 py-2">{p.document ?? "—"}</td>
                  <td className="px-4 py-2">{p.birthDate}</td>
                  <td className="px-4 py-2">{p.phone ?? "—"}</td>
                  <td className="px-4 py-2">
                    {p.status === "INACTIVE" ? (
                      <span className="text-muted-foreground">De baja</span>
                    ) : (
                      <span className="text-primary">Activo</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div className="flex items-center justify-between text-sm text-muted-foreground">
        <span>{total} en total</span>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
            Anterior
          </Button>
          <span>Página {page + 1}</span>
          <Button variant="outline" size="sm" disabled={isLast} onClick={() => setPage((p) => p + 1)}>
            Siguiente
          </Button>
        </div>
      </div>
    </main>
  );
}

function EmptyState({ hasQuery }: { hasQuery: boolean }) {
  return (
    <div className="flex flex-col items-center gap-3 py-12 text-center text-muted-foreground">
      <Plus className="size-8 opacity-40" />
      <p>{hasQuery ? "Ningún paciente coincide con la búsqueda." : "Aún no hay pacientes registrados."}</p>
    </div>
  );
}
