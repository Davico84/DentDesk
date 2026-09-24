import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { Odontogram } from "./Odontogram";

const CATALOG: DentalFindingCatalogEntryResponse[] = [
  { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false },
  { type: "CORONA", subtype: "CM", acronym: "CM", scope: "TOOTH", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: true },
  { type: "PROTESIS_PARCIAL_FIJA", subtype: "", acronym: undefined, scope: "TOOTH_RANGE", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: false },
  { type: "EDENTULO_TOTAL", subtype: "", acronym: undefined, scope: "ARCH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
];

const FINDINGS: DentalChartFindingResponse[] = [
  { id: 1, blockId: 1, type: "LESION_CARIES", subtype: "CD", location: { locationKind: "SURFACE", toothA: 26, surface: "OCLUSAL" } },
  { id: 2, blockId: 1, type: "CORONA", subtype: "CM", condition: "GOOD", location: { locationKind: "TOOTH", toothA: 36 } },
  { id: 3, blockId: 1, type: "PROTESIS_PARCIAL_FIJA", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH_RANGE", teeth: [14, 13, 12] } },
];

describe("Odontogram", () => {
  it("renders both dentitions with every FDI tooth focusable", () => {
    render(<Odontogram findings={[]} catalog={CATALOG} />);
    // 32 permanentes + 20 temporales = 52 piezas, cada una un botón (número FDI).
    expect(screen.getByRole("button", { name: "Pieza 26, sin hallazgos" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pieza 55, sin hallazgos" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pieza 85, sin hallazgos" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Pieza 48, sin hallazgos" })).toBeInTheDocument();
  });

  it("marca una superficie con hallazgo en rojo y la anuncia por aria-label", () => {
    render(<Odontogram findings={FINDINGS} catalog={CATALOG} />);
    expect(screen.getByRole("button", { name: /pieza 26, superficie oclusal: 1 hallazgo/i })).toBeInTheDocument();
  });

  it("un hallazgo de pieza completa se anuncia en el número FDI", () => {
    render(<Odontogram findings={FINDINGS} catalog={CATALOG} />);
    expect(screen.getByRole("button", { name: "Pieza 36, 1 hallazgo(s) de pieza" })).toBeInTheDocument();
  });

  it("un rango se anuncia como un solo elemento, no uno por pieza", () => {
    render(<Odontogram findings={FINDINGS} catalog={CATALOG} />);
    expect(screen.getByRole("img", { name: /rango de piezas 14, 13, 12/i })).toBeInTheDocument();
  });

  it("los arcos son focoseables incluso sin hallazgos", () => {
    render(<Odontogram findings={[]} catalog={CATALOG} />);
    expect(screen.getByRole("button", { name: /arco superior.*sin hallazgos de arco completo/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /arco inferior/i })).toBeInTheDocument();
  });

  it("clic en una pieza dispara onSelectLocation con TOOTH cuando es interactivo", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Odontogram findings={[]} catalog={CATALOG} onSelectLocation={onSelect} />);

    await user.click(screen.getByRole("button", { name: "Pieza 11, sin hallazgos" }));

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "TOOTH", toothA: 11 });
  });

  it("no interactivo (sellado): sigue siendo legible pero no dispara selección", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Odontogram findings={[]} catalog={CATALOG} interactive={false} onSelectLocation={onSelect} />);

    await user.click(screen.getByRole("button", { name: "Pieza 11, sin hallazgos" }));

    expect(onSelect).not.toHaveBeenCalled();
  });
});
