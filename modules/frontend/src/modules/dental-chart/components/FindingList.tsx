import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { useMediaQuery } from "@/modules/core/hooks/useMediaQuery";
import { useRemoveFinding } from "@/modules/dental-chart/hooks/useDentalChartApi";
import { PAPER, INK, FONT_BODY } from "@/modules/dental-chart/utils/chartPalette";
import { FindingTable } from "./FindingTable";
import { FindingCards } from "./FindingCards";

/** Desde aquí entran las cuatro columnas de la tabla sin cortarse (`sm` de Tailwind). */
const TABLE_QUERY = "(min-width: 640px)";

/** Lista de hallazgos ya registrados en el odontograma abierto, con opción de eliminar.
 *  Formato acordado con el user (artefacto de diseño): sigla/hallazgo/localización/estado
 *  — no solo el tipo y la sigla, para que se pueda auditar el odontograma sin leer el gráfico.
 *  Tabla desde 640 px; en celular, bloques. Se pinta UNA de las dos vistas (no ambas ocultando una
 *  con CSS), para no duplicar el contenido para lectores de pantalla (design D4). */
export function FindingList({
  patientId,
  findings,
  catalog,
  editable,
}: {
  patientId: number;
  findings: DentalChartFindingResponse[];
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  editable: boolean;
}) {
  const removeFinding = useRemoveFinding(patientId);
  const wide = useMediaQuery(TABLE_QUERY, true);

  if (findings.length === 0) {
    return <p className="text-sm text-muted-foreground">Sin hallazgos registrados todavía.</p>;
  }

  const onRemove = editable ? { run: (id: number) => removeFinding.mutate(id), pending: removeFinding.isPending } : undefined;

  // Mismo "papel" que el odontograma (chartPalette.ts): es parte del documento legal, así que no
  // cambia con el modo oscuro. Por eso el texto y los separadores también son fijos, no tokens.
  return (
    <div className="overflow-hidden rounded-lg border" style={{ background: PAPER, color: INK, fontFamily: FONT_BODY }}>
      {wide ? (
        <FindingTable findings={findings} catalog={catalog} onRemove={onRemove} />
      ) : (
        <FindingCards findings={findings} catalog={catalog} onRemove={onRemove} />
      )}
    </div>
  );
}
