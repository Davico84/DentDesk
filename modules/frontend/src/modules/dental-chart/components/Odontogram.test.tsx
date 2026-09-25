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
  { type: "PIEZA_SUPERNUMERARIA", subtype: "", acronym: "S", scope: "BETWEEN_TEETH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
  { type: "GEMINACION", subtype: "", acronym: undefined, scope: "TOOTH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
  { type: "ESPIGO_MUNON", subtype: "", acronym: undefined, scope: "TOOTH", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: false },
];

const FINDINGS: DentalChartFindingResponse[] = [
  { id: 1, blockId: 1, type: "LESION_CARIES", subtype: "CD", location: { locationKind: "SURFACE", toothA: 26, surface: "OCLUSAL" } },
  { id: 2, blockId: 1, type: "CORONA", subtype: "CM", condition: "GOOD", location: { locationKind: "TOOTH", toothA: 36 } },
  { id: 3, blockId: 1, type: "PROTESIS_PARCIAL_FIJA", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH_RANGE", teeth: [14, 13, 12] } },
  { id: 4, blockId: 1, type: "PIEZA_SUPERNUMERARIA", subtype: "", location: { locationKind: "BETWEEN_TEETH", toothA: 11, toothB: 21 } },
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

  /**
   * Regresión: sin sigla oficial (GEMINACIÓN no tiene, `FindingType.java`), el recuadro de sigla
   * mostraba el nombre entero del tipo ("GEMINACION") y se desbordaba — el user lo encontró
   * probando en vivo. Verificado contra el PDF oficial (6.1.12): el símbolo correcto es un círculo
   * azul alrededor del número FDI, no una sigla en el recuadro.
   */
  it("sin sigla oficial (geminación), no desborda el recuadro y dibuja el círculo en el número", () => {
    const geminacion: DentalChartFindingResponse = { id: 5, blockId: 1, type: "GEMINACION", subtype: "", location: { locationKind: "TOOTH", toothA: 15 } };
    render(<Odontogram findings={[geminacion]} catalog={CATALOG} />);

    // El recuadro de sigla no debe llevar el texto crudo del tipo.
    expect(screen.queryByText("GEMINACION")).not.toBeInTheDocument();
    // El círculo alrededor del número sí debe existir.
    const numberButton = screen.getByRole("button", { name: "Pieza 15, 1 hallazgo(s) de pieza" });
    expect(numberButton.querySelector("ellipse")).not.toBeNull();
  });

  /**
   * Regresión: ESPIGO-MUÑÓN (sin sigla oficial) caía en el mismo anillo genérico que CORONA,
   * MOVILIDAD, etc. — el user lo encontró probando en vivo ("veo el círculo sobre toda la pieza
   * que se confunde con otros hallazgos"). Verificado contra el PDF oficial (6.1.8): "línea
   * vertical en la raíz unida a un cuadrado en la corona", no un anillo.
   */
  it("espigo-muñón dibuja línea+cuadrado, no el anillo genérico de corona", () => {
    const espigoMunon: DentalChartFindingResponse = { id: 6, blockId: 1, type: "ESPIGO_MUNON", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH", toothA: 16 } };
    render(<Odontogram findings={[espigoMunon]} catalog={CATALOG} />);

    const numberButton = screen.getByRole("button", { name: "Pieza 16, 1 hallazgo(s) de pieza" });
    const crownGroup = numberButton.parentElement!.querySelector("g > polygon")!.closest("g")!;
    expect(crownGroup.querySelector("rect[rx='4']")).toBeNull(); // sin anillo genérico
    expect(crownGroup.querySelector("line")).not.toBeNull(); // línea de la raíz
    expect(crownGroup.querySelectorAll("rect")).toHaveLength(1); // el cuadrado del espigo-muñón
  });

  /**
   * Regresión: el marcador de entre-piezas quedaba mezclado con el carril de arco/rango, sin
   * posición real (el user lo encontró probando en vivo: un círculo genérico arriba de la
   * grilla, no entre las piezas 11 y 21). Debe estar pegado a las piezas reales que conecta, no
   * en el carril externo (fuera del rango vertical de los carriles superior/inferior).
   */
  it("un entre-piezas se dibuja pegado a las dos piezas reales, no en el carril de arco/rango", () => {
    const { container } = render(<Odontogram findings={FINDINGS} catalog={CATALOG} />);
    const marker = screen.getByRole("img", { name: /entre las piezas 11 y 21: s/i });
    const circle = marker.querySelector("circle");
    expect(circle).not.toBeNull();

    const svg = container.querySelector("svg")!;
    const svgHeight = Number(svg.getAttribute("height"));
    const cy = Number(circle!.getAttribute("cy"));
    // LANE_H = 56: ni en el carril superior (cy < 56) ni en el inferior (cy > height - 56).
    expect(cy).toBeGreaterThan(56);
    expect(cy).toBeLessThan(svgHeight - 56);
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
