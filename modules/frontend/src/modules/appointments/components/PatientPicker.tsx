import { useState } from "react";
import { Search, X } from "lucide-react";
import { IconInput } from "@/modules/core/ui/icon-input";
import { Button } from "@/modules/core/ui/button";
import { Label } from "@/modules/core/ui/label";
import { useDebouncedValue } from "@/modules/core/hooks/useDebouncedValue";
import { usePatient, usePatientsSearch } from "@/modules/patients/hooks/usePatientsApi";

/**
 * Elegir el paciente de una cita reutilizando la búsqueda de `patients` (nombre o documento, solo activos). Con un
 * paciente ya elegido (o precargado desde la nota) muestra su nombre y permite cambiarlo.
 */
export function PatientPicker({
  value,
  onChange,
  error,
}: {
  value: string;
  onChange: (patientId: string) => void;
  error?: string;
}) {
  const [query, setQuery] = useState("");
  const debounced = useDebouncedValue(query.trim(), 250);
  const selected = usePatient(value ? Number(value) : undefined);
  const results = usePatientsSearch({ query: debounced || undefined, includeInactive: false, page: 0, size: 6 });
  const errorId = "patientId-error";

  if (value) {
    return (
      <div className="flex flex-col gap-1.5">
        <Label>Paciente</Label>
        <div className="flex h-11 items-center justify-between gap-2 rounded-xl border border-input bg-card px-3 text-sm">
          <span className="truncate">{selected.data?.fullName ?? "Cargando…"}</span>
          <Button type="button" variant="ghost" size="sm" onClick={() => onChange("")} aria-label="Cambiar de paciente">
            <X className="size-4" />
          </Button>
        </div>
      </div>
    );
  }

  const patients = results.data?.content ?? [];
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor="patient-search">
        Paciente <span className="text-destructive">*</span>
      </Label>
      <IconInput
        id="patient-search"
        icon={<Search className="size-4" />}
        placeholder="Buscar por nombre o documento"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        hasError={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        className="h-11 rounded-xl"
      />
      <ul aria-label="Pacientes encontrados" className="flex max-h-48 flex-col overflow-y-auto rounded-xl border border-border">
        {patients.length === 0 ? (
          <li className="px-3 py-2 text-sm text-muted-foreground">
            {results.isLoading ? "Buscando…" : "No hay pacientes con esa búsqueda."}
          </li>
        ) : (
          patients.map((patient) => (
            <li key={patient.id}>
              <button
                type="button"
                onClick={() => onChange(String(patient.id))}
                className="w-full px-3 py-2 text-left text-sm hover:bg-accent focus-visible:bg-accent focus-visible:outline-hidden"
              >
                {patient.fullName}
                {patient.document ? (
                  <span className="ml-2 text-xs text-muted-foreground">{patient.document}</span>
                ) : null}
              </button>
            </li>
          ))
        )}
      </ul>
      {error ? (
        <p id={errorId} role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
