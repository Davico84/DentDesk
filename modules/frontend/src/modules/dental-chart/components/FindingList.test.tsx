import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { FindingList } from "./FindingList";

const CATALOG: DentalFindingCatalogEntryResponse[] = [
  { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false },
  { type: "CORONA", subtype: "CM", acronym: "CM", scope: "TOOTH", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: true },
  { type: "GEMINACION", subtype: "", acronym: undefined, scope: "TOOTH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
];

const FINDINGS: DentalChartFindingResponse[] = [
  { id: 1, blockId: 1, type: "LESION_CARIES", subtype: "CD", location: { locationKind: "SURFACE", toothA: 26, surface: "OCLUSAL" } },
  { id: 2, blockId: 1, type: "CORONA", subtype: "CM", condition: "GOOD", location: { locationKind: "TOOTH", toothA: 36 } },
];

describe("FindingList", () => {
  it("sin hallazgos, muestra el mensaje vacío y no la tabla", () => {
    renderWithProviders(<FindingList patientId={1} findings={[]} catalog={CATALOG} editable />);

    expect(screen.getByText("Sin hallazgos registrados todavía.")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  /**
   * El user pidió que la lista muestre lo mismo que el artefacto de diseño: sigla, hallazgo
   * completo, localización legible y estado — no solo el tipo y la sigla, como antes.
   */
  it("muestra sigla, hallazgo, localización y estado por cada fila", () => {
    renderWithProviders(<FindingList patientId={1} findings={FINDINGS} catalog={CATALOG} editable />);

    const rows = screen.getAllByRole("row");
    expect(rows).toHaveLength(FINDINGS.length + 1); // + encabezado

    expect(screen.getByRole("cell", { name: "CD" })).toBeInTheDocument();
    expect(screen.getByText("Pieza 26, superficie OCLUSAL")).toBeInTheDocument();
    expect(screen.getByText("Por hacer")).toBeInTheDocument(); // LESION_CARIES es SIEMPRE_ROJO

    expect(screen.getByRole("cell", { name: "CM" })).toBeInTheDocument();
    expect(screen.getByText("Pieza 36")).toBeInTheDocument();
    expect(screen.getByText("Realizado")).toBeInTheDocument(); // CORONA condition GOOD
  });

  it("un tipo sin sigla oficial muestra un guión, no vacío", () => {
    const geminacion: DentalChartFindingResponse[] = [{ id: 3, blockId: 1, type: "GEMINACION", subtype: "", location: { locationKind: "TOOTH", toothA: 15 } }];
    renderWithProviders(<FindingList patientId={1} findings={geminacion} catalog={CATALOG} editable />);

    expect(screen.getByRole("cell", { name: "—" })).toBeInTheDocument();
  });

  it("no editable: no muestra el botón de eliminar", () => {
    renderWithProviders(<FindingList patientId={1} findings={FINDINGS} catalog={CATALOG} editable={false} />);

    expect(screen.queryByRole("button", { name: /eliminar hallazgo/i })).not.toBeInTheDocument();
  });

  it("editable: eliminar dispara el DELETE del hallazgo", async () => {
    let deletedId: number | null = null;
    server.use(
      http.delete("*/patients/1/dental-chart/findings/:findingId", ({ params }) => {
        deletedId = Number(params.findingId);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<FindingList patientId={1} findings={FINDINGS} catalog={CATALOG} editable />);

    await user.click(screen.getByRole("button", { name: /eliminar hallazgo lesión de caries/i }));

    expect(deletedId).toBe(1);
  });
});
