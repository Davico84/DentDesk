import { useRef, useState } from "react";
import { Search } from "lucide-react";
import { Input } from "@/modules/core/ui/input";
import { Label } from "@/modules/core/ui/label";
import { useDebouncedValue } from "@/modules/core/hooks/useDebouncedValue";
import { useDismissOnOutsideOrEscape } from "@/modules/core/hooks/useDismissOnOutsideOrEscape";
import { useCie10Search } from "@/modules/clinical-history/hooks/useClinicalHistoryApi";
import type { Cie10SearchResultResponse } from "@/modules/core/services/generated/model";

/**
 * Buscador del catálogo CIE10 que **navega la jerarquía** (spec: búsqueda jerárquica) — cada
 * resultado muestra su ruta completa (capítulo → grupo → categoría → subcategoría), nunca una
 * lista plana de códigos sueltos.
 */
export function Cie10SearchField({
  onSelect,
  selectedCode,
  selectedDescription,
}: {
  onSelect: (result: Cie10SearchResultResponse | null) => void;
  selectedCode?: string;
  selectedDescription?: string;
}) {
  const [query, setQuery] = useState("");
  const debouncedQuery = useDebouncedValue(query, 300);
  const { data: results, isFetching } = useCie10Search(debouncedQuery);
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  useDismissOnOutsideOrEscape(containerRef, open, () => setOpen(false));

  return (
    <div className="flex flex-col gap-1.5 sm:col-span-2">
      <Label htmlFor="cie10-search">Diagnóstico (CIE10)</Label>
      {selectedCode ? (
        <div className="flex items-center justify-between rounded-xl border bg-accent px-3 py-2 text-sm">
          <span>
            <strong>{selectedCode}</strong> — {selectedDescription}
          </span>
          <button
            type="button"
            className="text-xs text-muted-foreground underline underline-offset-2 hover:no-underline"
            onClick={() => {
              setQuery("");
              setOpen(false);
              onSelect(null);
            }}
          >
            Cambiar
          </button>
        </div>
      ) : (
        <div ref={containerRef} className="relative">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              id="cie10-search"
              className="h-11 rounded-xl pl-9"
              placeholder="Busca por nombre o código (mín. 2 caracteres)"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value);
                setOpen(true);
              }}
              onFocus={() => setOpen(true)}
            />
          </div>
          {open && debouncedQuery.trim().length >= 2 ? (
            <ul className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-xl border bg-card shadow-lg">
              {isFetching ? (
                <li className="px-3 py-2 text-sm text-muted-foreground">Buscando...</li>
              ) : results && results.length > 0 ? (
                results.map((result) => (
                  <li key={result.code}>
                    <button
                      type="button"
                      className="flex w-full flex-col gap-0.5 px-3 py-2 text-left text-sm hover:bg-muted/50"
                      onClick={() => {
                        onSelect(result);
                        setQuery("");
                        setOpen(false);
                      }}
                    >
                      <span className="font-medium">
                        {result.code} — {result.descripcion}
                      </span>
                      {/* La jerarquía completa, no un código suelto: capítulo → grupo → categoría → subcategoría. */}
                      <span className="text-xs text-muted-foreground">
                        {(result.path ?? []).map((segment) => segment.descripcion).join(" › ")}
                      </span>
                    </button>
                  </li>
                ))
              ) : (
                <li className="px-3 py-2 text-sm text-muted-foreground">Sin resultados.</li>
              )}
            </ul>
          ) : null}
        </div>
      )}
    </div>
  );
}
