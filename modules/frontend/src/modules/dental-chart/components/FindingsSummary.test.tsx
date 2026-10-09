import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { FindingsSummary } from "./FindingsSummary";

const CATALOG: DentalFindingCatalogEntryResponse[] = [
  { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false },
  { type: "GEMINACION", subtype: "", acronym: undefined, scope: "TOOTH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
  { type: "CORONA", subtype: "CM", acronym: "CM", scope: "TOOTH", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: true },
];

describe("FindingsSummary", () => {
  it("sin hallazgos, muestra 0 en cada conteo", () => {
    render(<FindingsSummary findings={[]} catalog={CATALOG} />);
    expect(screen.getByText("0", { selector: ".font-mono.text-3xl" })).toBeInTheDocument();
  });

  it("desglosa por hacer (rojo) vs realizado (azul) igual que el color normativo del odontograma", () => {
    const findings: DentalChartFindingResponse[] = [
      { id: 1, blockId: 1, type: "LESION_CARIES", subtype: "CD", location: { locationKind: "SURFACE", toothA: 26, surface: "OCLUSAL" } }, // siempre rojo
      { id: 2, blockId: 1, type: "GEMINACION", subtype: "", location: { locationKind: "TOOTH", toothA: 15 } }, // siempre azul
      { id: 3, blockId: 1, type: "CORONA", subtype: "CM", condition: "POOR", location: { locationKind: "TOOTH", toothA: 36 } }, // según estado: mal estado = rojo
    ];
    render(<FindingsSummary findings={findings} catalog={CATALOG} />);

    expect(screen.getByText("3", { selector: ".font-mono.text-3xl" })).toBeInTheDocument();
    expect(screen.getByText("hallazgos")).toBeInTheDocument();
    expect(screen.getByText(/por hacer/i)).toBeInTheDocument();
    expect(screen.getByText(/realizado/i)).toBeInTheDocument();
    // 2 rojos (caries + corona en mal estado), 1 azul (geminación).
    const counts = screen.getAllByText(/^[0-9]$/).map((el) => el.textContent);
    expect(counts).toEqual(expect.arrayContaining(["2", "1"]));
  });
});
