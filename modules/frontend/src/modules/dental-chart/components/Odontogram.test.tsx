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
  { type: "MACRODONCIA", subtype: "", acronym: "MAC", scope: "TOOTH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
  { type: "MOVILIDAD_PATOLOGICA", subtype: "", acronym: "M", scope: "TOOTH", colorBehavior: "SIEMPRE_ROJO", requiresGrade: true, requiresSpecification: false },
  { type: "PIEZA_AUSENTE", subtype: "DNE", acronym: "DNE", scope: "TOOTH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
  { type: "FRACTURA_DENTAL", subtype: "", acronym: undefined, scope: "TOOTH", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false },
  { type: "DIASTEMA", subtype: "", acronym: undefined, scope: "BETWEEN_TEETH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
  { type: "APARATO_ORTODONTICO_REMOVIBLE", subtype: "", acronym: undefined, scope: "ARCH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
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
  it("espigo-muñón dibuja línea+cuadrado en el recuadro de sigla, nunca sobre la corona", () => {
    const espigoMunon: DentalChartFindingResponse = { id: 6, blockId: 1, type: "ESPIGO_MUNON", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH", toothA: 16 } };
    render(<Odontogram findings={[espigoMunon]} catalog={CATALOG} />);

    const numberButton = screen.getByRole("button", { name: "Pieza 16, 1 hallazgo(s) de pieza" });
    const toothGroup = numberButton.parentElement!;
    const crownGroup = toothGroup.querySelector("g > polygon")!.closest("g")!;

    // Regresión: el glifo quedaba superpuesto a la cara superior de la corona y se confundía con
    // un hallazgo de superficie (el user lo notó probando en vivo). No debe haber NADA propio del
    // espigo-muñón dentro de la corona — ni anillo genérico ni el glifo.
    expect(crownGroup.querySelector("rect")).toBeNull();
    expect(crownGroup.querySelector("line")).toBeNull();

    // El glifo vive en el recuadro de sigla (vacío para este tipo, sin sigla oficial).
    const line = toothGroup.querySelector("line");
    expect(line).not.toBeNull();
    expect(crownGroup.contains(line)).toBe(false);
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

  /**
   * Regresión: la barra del carril superior quedaba pegada (llegaba a solapar 1-2px) con el borde
   * de la primera fila de piezas — el user lo notó probando en vivo.
   */
  it("la barra del carril superior no toca la primera fila de piezas", () => {
    render(<Odontogram findings={[]} catalog={CATALOG} />);
    const archButton = screen.getByRole("button", { name: /arco superior/i });
    const bar = archButton.querySelector("rect")!;
    const barBottom = Number(bar.getAttribute("y")) + Number(bar.getAttribute("height"));

    // LANE_H = 56: la primera fila empieza ahí — la barra debe terminar antes, con margen.
    expect(barBottom).toBeLessThan(56);
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

  /**
   * Auditoría completa contra el PDF oficial (2026-09-25): el anillo alrededor de la corona
   * ("cuadrado bordeando la corona") es SOLO para CORONA/CORONA_TEMPORAL (6.1.3/6.1.4) — no un
   * símbolo genérico de "pieza con hallazgo". Antes de esta auditoría, cualquier tipo TOOTH con
   * sigla (macrodoncia, impactación, implante...) caía en el mismo anillo genérico, cosa que el
   * PDF no pide para ellos: solo muestran su sigla en el recuadro.
   */
  it("un tipo con sigla que no es corona (macrodoncia) NO dibuja el anillo genérico", () => {
    const macrodoncia: DentalChartFindingResponse = { id: 10, blockId: 1, type: "MACRODONCIA", subtype: "", location: { locationKind: "TOOTH", toothA: 15 } };
    render(<Odontogram findings={[macrodoncia]} catalog={CATALOG} />);

    const numberButton = screen.getByRole("button", { name: "Pieza 15, 1 hallazgo(s) de pieza" });
    const crownGroup = numberButton.parentElement!.querySelector("g > polygon")!.closest("g")!;
    expect(crownGroup.querySelector("rect")).toBeNull();
    expect(screen.getByText("MAC")).toBeInTheDocument();
  });

  /**
   * 6.1.19 MOVILIDAD PATOLÓGICA: la sigla lleva el grado pegado ("M2"), no la sigla sola.
   */
  it("movilidad patológica muestra el grado pegado a la sigla (M2, no M)", () => {
    const movilidad: DentalChartFindingResponse = { id: 11, blockId: 1, type: "MOVILIDAD_PATOLOGICA", subtype: "", grade: 2, location: { locationKind: "TOOTH", toothA: 24 } };
    render(<Odontogram findings={[movilidad]} catalog={CATALOG} />);

    expect(screen.getByText("M2")).toBeInTheDocument();
    expect(screen.queryByText(/^M$/)).not.toBeInTheDocument();
  });

  /**
   * 6.1.20 PIEZA AUSENTE: aspa sobre la corona, ADEMÁS de la sigla (DNE/DEX/DAO) en el recuadro —
   * no es solo una sigla como el resto de tipos TOOTH con acrónimo.
   */
  it("pieza ausente dibuja un aspa sobre la corona además de la sigla", () => {
    const ausente: DentalChartFindingResponse = { id: 12, blockId: 1, type: "PIEZA_AUSENTE", subtype: "DNE", location: { locationKind: "TOOTH", toothA: 46 } };
    render(<Odontogram findings={[ausente]} catalog={CATALOG} />);

    const numberButton = screen.getByRole("button", { name: "Pieza 46, 1 hallazgo(s) de pieza" });
    const crownGroup = numberButton.parentElement!.querySelector("g > polygon")!.closest("g")!;
    expect(crownGroup.querySelectorAll("line")).toHaveLength(2);
    expect(screen.getByText("DNE")).toBeInTheDocument();
  });

  /**
   * 6.1.10 FRACTURA DENTAL: línea representando la fractura sobre la corona/raíz — sin sigla
   * oficial, el símbolo va sobre la figura de la corona (no en el recuadro, a diferencia de
   * espigo-muñón que sí va fuera).
   */
  it("fractura dental dibuja una línea sobre la corona, sin anillo genérico", () => {
    const fractura: DentalChartFindingResponse = { id: 13, blockId: 1, type: "FRACTURA_DENTAL", subtype: "", location: { locationKind: "TOOTH", toothA: 21 } };
    render(<Odontogram findings={[fractura]} catalog={CATALOG} />);

    const numberButton = screen.getByRole("button", { name: "Pieza 21, 1 hallazgo(s) de pieza" });
    const crownGroup = numberButton.parentElement!.querySelector("g > polygon")!.closest("g")!;
    expect(crownGroup.querySelectorAll("line")).toHaveLength(1);
    expect(crownGroup.querySelector("rect")).toBeNull();
  });

  /**
   * 6.1.6 DIASTEMA: signo del paréntesis invertido, distinto del círculo con sigla que sí aplica
   * a supernumeraria (6.1.26) — no todos los entre-piezas comparten el mismo símbolo.
   */
  it("diastema dibuja el signo de paréntesis invertido, no el círculo de supernumeraria", () => {
    const diastema: DentalChartFindingResponse = { id: 14, blockId: 1, type: "DIASTEMA", subtype: "", location: { locationKind: "BETWEEN_TEETH", toothA: 11, toothB: 21 } };
    render(<Odontogram findings={[diastema]} catalog={CATALOG} />);

    const marker = screen.getByRole("img", { name: /entre las piezas 11 y 21/i });
    expect(marker.querySelectorAll("path")).toHaveLength(2);
    expect(marker.querySelector("circle")).toBeNull();
  });

  /**
   * 6.1.2 APARATO ORTODÓNTICO REMOVIBLE: línea en zig-zag a la altura de los ápices — distinto de
   * la línea recta de edéntulo total (6.1.7) aunque ambos sean de arco completo.
   */
  it("aparato ortodóntico removible dibuja zig-zag, no la barra genérica de arco", () => {
    const aparato: DentalChartFindingResponse = { id: 15, blockId: 1, type: "APARATO_ORTODONTICO_REMOVIBLE", subtype: "", location: { locationKind: "ARCH", arch: "INFERIOR" } };
    render(<Odontogram findings={[aparato]} catalog={CATALOG} />);

    const archButton = screen.getByRole("button", { name: /arco inferior/i });
    expect(archButton.querySelector("polyline")).not.toBeNull();
    expect(archButton.querySelector("rect")).toBeNull();
  });
});
