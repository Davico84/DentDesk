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
  { type: "APARATO_ORTODONTICO_FIJO", subtype: "", acronym: undefined, scope: "TOOTH_RANGE", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: false },
  { type: "PROTESIS_PARCIAL_REMOVIBLE", subtype: "", acronym: undefined, scope: "TOOTH_RANGE", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: false },
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
    // Ni en el carril superior de arco ni en el inferior (ambos bien menores a 56px de margen).
    expect(cy).toBeGreaterThan(56);
    expect(cy).toBeLessThan(svgHeight - 56);
  });

  /**
   * Regresión: la barra del carril superior quedaba pegada (llegaba a solapar 1-2px) con el borde
   * de la primera fila de piezas — el user lo notó probando en vivo.
   */
  it("la barra del carril superior no toca la primera fila de piezas", () => {
    render(<Odontogram findings={[]} catalog={CATALOG} />);
    const archButton = screen.getByRole("button", { name: /arco superior[:,]/i });
    const bar = archButton.querySelector("rect:not([data-hit-area])")!;
    const barBottom = Number(bar.getAttribute("y")) + Number(bar.getAttribute("height"));

    // El carril de arco mide LANE_H (44px) — la barra debe quedar bien antes de que termine.
    expect(barBottom).toBeLessThan(44);
  });

  it("los arcos son focoseables incluso sin hallazgos", () => {
    render(<Odontogram findings={[]} catalog={CATALOG} />);
    expect(screen.getByRole("button", { name: /arco superior.*sin hallazgos de arco completo/i })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /arco inferior[:,]/i })).toBeInTheDocument();
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

    const archButton = screen.getByRole("button", { name: /arco inferior[:,]/i });
    expect(archButton.querySelector("polyline")).not.toBeNull();
    expect(archButton.querySelector("rect:not([data-hit-area])")).toBeNull();
  });

  /**
   * 5.8b: entre-piezas ahora es clickeable directo en el gráfico (sin modo), en la franja entre
   * dos piezas adyacentes reales — antes solo se podía registrar desde el formulario suelto.
   */
  it("clic en la franja entre dos piezas adyacentes dispara BETWEEN_TEETH con ambos FDI", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Odontogram findings={[]} catalog={CATALOG} onSelectLocation={onSelect} />);

    await user.click(screen.getByRole("button", { name: "Entre las piezas 11 y 21" }));

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "BETWEEN_TEETH", toothA: 11, toothB: 21 });
  });

  /**
   * 5.8b: modo rango — primer click ancla (sin disparar onSelectLocation todavía), segundo click
   * en la misma fila cierra el rango con las piezas contiguas entre ambos, inclusive.
   */
  it("modo rango: dos clicks en la misma fila arman el rango contiguo entre ambos", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Odontogram findings={[]} catalog={CATALOG} rangeMode onSelectLocation={onSelect} />);

    await user.click(screen.getByRole("button", { name: "Pieza 14, sin hallazgos" }));
    expect(onSelect).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Pieza 12, sin hallazgos" }));

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "TOOTH_RANGE", teeth: [14, 13, 12] });
  });

  it("modo rango: segundo click en otra fila re-ancla en vez de romper", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Odontogram findings={[]} catalog={CATALOG} rangeMode onSelectLocation={onSelect} />);

    await user.click(screen.getByRole("button", { name: "Pieza 14, sin hallazgos" }));
    await user.click(screen.getByRole("button", { name: "Pieza 44, sin hallazgos" })); // otra fila: re-ancla, no dispara
    expect(onSelect).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Pieza 43, sin hallazgos" })); // ahora sí, misma fila que 44
    expect(onSelect).toHaveBeenCalledWith({ locationKind: "TOOTH_RANGE", teeth: [44, 43] });
  });

  it("modo rango: Escape cancela el ancla sin disparar onSelectLocation", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Odontogram findings={[]} catalog={CATALOG} rangeMode onSelectLocation={onSelect} />);

    await user.click(screen.getByRole("button", { name: "Pieza 14, sin hallazgos" }));
    await user.keyboard("{Escape}");
    await user.click(screen.getByRole("button", { name: "Pieza 12, sin hallazgos" })); // vuelve a anclar, no cierra rango

    expect(onSelect).not.toHaveBeenCalled();
  });

  it("modo rango: superficie y arco no disparan selección (un solo gesto a la vez)", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Odontogram findings={[]} catalog={CATALOG} rangeMode onSelectLocation={onSelect} />);

    await user.click(screen.getByRole("button", { name: /pieza 26, superficie oclusal/i }));
    await user.click(screen.getByRole("button", { name: /arco superior[:,]/i }));

    expect(onSelect).not.toHaveBeenCalled();
  });

  /**
   * Regresión: un rango con piezas de una fila TEMPORAL caía en el carril externo de arriba de
   * TODO (compartido con la fila permanente), lejos de su fila real — el user lo encontró
   * registrando un aparato fijo en 54-52 y viéndolo aparecer pegado a la fila 18-11/21-28. El
   * marcador debe quedar en la franja pegada a SU fila (54-51), no en el carril externo (y<56).
   */
  it("un rango con piezas temporales se dibuja junto a su propia fila, no en el carril externo", () => {
    const rango: DentalChartFindingResponse = {
      id: 20,
      blockId: 1,
      type: "PROTESIS_PARCIAL_FIJA",
      subtype: "",
      condition: "GOOD",
      location: { locationKind: "TOOTH_RANGE", teeth: [54, 53, 52] },
    };
    render(<Odontogram findings={[rango]} catalog={CATALOG} />);

    const marker = screen.getByRole("img", { name: /rango de piezas 54, 53, 52/i });
    const line = marker.querySelector("line")!;
    const y = Number(line.getAttribute("y1"));

    // El carril externo de arco es angosto — el marcador debe caer bien después, en la franja
    // propia de su fila (temporal superior), no ahí.
    expect(y).toBeGreaterThan(100);
  });

  /**
   * Regresión: un rango en la fila temporal (caso real del user: aparato fijo 54-52) quedaba casi
   * pegado al borde de la corona de la fila vecina — "está pegada al código de un hallazgo",
   * "sobre la línea gris" (probado en vivo). El marcador debe quedar con margen real respecto al
   * borde inferior de la corona permanente (y=166, sin rangos en esa fila la franja mide el mínimo)
   * y al recuadro de sigla de la fila temporal (y=198). Como 54-52 y 61-64 no se solapan en X
   * (piezas de lados distintos), comparten carril — no es un caso de apilado, ver el test de
   * lane-packing más abajo para eso.
   */
  it("dos rangos que no se solapan en la misma fila temporal no quedan pegados a la fila vecina", () => {
    const fijo: DentalChartFindingResponse = {
      id: 21,
      blockId: 1,
      type: "APARATO_ORTODONTICO_FIJO",
      subtype: "",
      condition: "GOOD",
      location: { locationKind: "TOOTH_RANGE", teeth: [54, 53, 52] },
    };
    const removible: DentalChartFindingResponse = {
      id: 22,
      blockId: 1,
      type: "PROTESIS_PARCIAL_REMOVIBLE",
      subtype: "",
      condition: "GOOD",
      location: { locationKind: "TOOTH_RANGE", teeth: [61, 62, 63, 64] },
    };
    render(<Odontogram findings={[fijo, removible]} catalog={CATALOG} />);

    const markers = [
      screen.getByRole("img", { name: /rango de piezas 54, 53, 52/i }),
      screen.getByRole("img", { name: /rango de piezas 61, 62, 63, 64/i }),
    ];
    const ys = markers.map((m) => Number(m.querySelector("text")!.getAttribute("y")));

    // Fila permanente termina en y=166 (borde de la corona), fila temporal empieza en y=198
    // (recuadro de sigla) — ningún marcador debe caer a menos de 4px de ninguno de los dos.
    for (const y of ys) {
      expect(y).toBeGreaterThan(170);
      expect(y).toBeLessThan(194);
    }
    // Sin solaparse en X, comparten el mismo carril.
    expect(ys[0]).toBe(ys[1]);
  });

  /**
   * Regresión: con TRES rangos en la fila PERMANENTE que NO se solapan entre sí en X (caso real
   * del user: aparato fijo 17-14 + prótesis parcial fija 13-11 + prótesis parcial removible
   * 21-24 — piezas distintas, sin tocarse), deben quedar los 3 a la MISMA altura, no apilados —
   * apilar sin necesidad confundía al user probando en vivo ("¿por qué a distinta altura si no
   * se tocan?"). Apilar es solo para cuando SÍ se solapan en X (ver siguiente test).
   */
  it("tres rangos que no se solapan en X quedan a la misma altura, no apilados", () => {
    const findings: DentalChartFindingResponse[] = [
      { id: 30, blockId: 1, type: "APARATO_ORTODONTICO_FIJO", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH_RANGE", teeth: [17, 16, 15, 14] } },
      { id: 31, blockId: 1, type: "PROTESIS_PARCIAL_FIJA", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH_RANGE", teeth: [13, 12, 11] } },
      { id: 32, blockId: 1, type: "PROTESIS_PARCIAL_REMOVIBLE", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH_RANGE", teeth: [21, 22, 23, 24] } },
    ];
    render(<Odontogram findings={findings} catalog={CATALOG} />);

    const markers = [
      screen.getByRole("img", { name: /rango de piezas 17, 16, 15, 14/i }),
      screen.getByRole("img", { name: /rango de piezas 13, 12, 11/i }),
      screen.getByRole("img", { name: /rango de piezas 21, 22, 23, 24/i }),
    ];
    // La sigla (<text>) siempre se posiciona en trackY-4, sin el offset propio de cada glifo (ej.
    // prótesis parcial removible dibuja 2 líneas a ±2px del centro) — es el proxy fiable del carril.
    const ys = markers.map((m) => Number(m.querySelector("text")!.getAttribute("y")));

    // Sin solaparse, los 3 comparten un solo carril — la franja mide lo justo para uno (32px):
    // de y=44 (fin del carril de arco) a y=76 (inicio de la corona).
    for (const y of ys) {
      expect(y).toBeGreaterThan(48);
      expect(y).toBeLessThan(72);
    }
    // Sin solaparse en X, comparten el mismo carril: misma altura para los 3.
    expect(new Set(ys).size).toBe(1);
  });

  /**
   * Complemento del test anterior: dos rangos que SÍ se solapan en X (mismas piezas) deben
   * apilarse en carriles distintos — el empaquetado por solapamiento, no por índice.
   */
  it("dos rangos que sí se solapan en X se apilan en carriles distintos", () => {
    const findings: DentalChartFindingResponse[] = [
      { id: 33, blockId: 1, type: "APARATO_ORTODONTICO_FIJO", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH_RANGE", teeth: [17, 16, 15, 14] } },
      { id: 34, blockId: 1, type: "PROTESIS_PARCIAL_FIJA", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH_RANGE", teeth: [16, 15] } },
    ];
    render(<Odontogram findings={findings} catalog={CATALOG} />);

    const markers = [
      screen.getByRole("img", { name: /^rango de piezas 17, 16, 15, 14:/i }),
      screen.getByRole("img", { name: /^rango de piezas 16, 15:/i }),
    ];
    const ys = markers.map((m) => Number(m.querySelector("text")!.getAttribute("y")));

    expect(new Set(ys).size).toBe(2);
  });

  /**
   * El user notó que sacar el texto "Arco"/"Rango" (sin info una vez que ya hay botones) dejó un
   * espacio en blanco sin usar: las franjas medían un alto FIJO aunque no tuvieran ningún rango.
   * Ahora cada franja mide según cuántos carriles necesita de verdad (`packRangeLanes` +
   * `rangeStripHeight`) — sin rangos, el odontograma entero debe ser más bajo que con 3
   * concurrentes en la misma fila.
   */
  it("sin rangos, el odontograma es más bajo que con varios concurrentes (la franja no es un tamaño fijo)", () => {
    const { container: empty } = render(<Odontogram findings={[]} catalog={CATALOG} />);
    const { container: busy } = render(
      <Odontogram
        findings={[
          { id: 40, blockId: 1, type: "APARATO_ORTODONTICO_FIJO", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH_RANGE", teeth: [17, 16, 15, 14] } },
          { id: 41, blockId: 1, type: "PROTESIS_PARCIAL_FIJA", subtype: "", condition: "GOOD", location: { locationKind: "TOOTH_RANGE", teeth: [16, 15] } },
        ]}
        catalog={CATALOG}
      />,
    );

    const emptyHeight = Number(empty.querySelector("svg")!.getAttribute("height"));
    const busyHeight = Number(busy.querySelector("svg")!.getAttribute("height"));

    expect(busyHeight).toBeGreaterThan(emptyHeight);
  });

  /**
   * Regresión: el objetivo de click de "arco" era la línea/glifo visual, de 2-3px de grosor —
   * el user no lograba encontrarlo probando en vivo con el mouse. El objetivo real debe cubrir
   * toda la franja (alto LANE_H), no solo el trazo fino.
   */
  it("el botón de arco es clickeable en toda la franja, no solo sobre la línea fina", () => {
    render(<Odontogram findings={[]} catalog={CATALOG} />);
    const archButton = screen.getByRole("button", { name: /arco superior[:,]/i });
    const hitArea = archButton.querySelector("rect[data-hit-area]")!;

    expect(Number(hitArea.getAttribute("height"))).toBe(44);
    expect(Number(hitArea.getAttribute("y"))).toBe(0);
  });

  /**
   * El user pidió que el botón de arco no se sienta "ajeno" al odontograma (como el resumen de
   * hallazgos) — ambos viven ahora DENTRO del borde del componente, no en la pantalla que lo
   * aloja. Un click en el botón de texto debe disparar lo mismo que clickear la franja del SVG.
   */
  it("el botón de texto \"Arco superior\" (toolbar propia) dispara lo mismo que la franja del SVG", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Odontogram findings={[]} catalog={CATALOG} onSelectLocation={onSelect} />);

    await user.click(screen.getByRole("button", { name: "Arco superior" }));

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "ARCH", arch: "SUPERIOR" });
  });

  it("sin onToggleRangeMode, no muestra el botón de rango (no tendría con qué togglear)", () => {
    render(<Odontogram findings={[]} catalog={CATALOG} />);
    expect(screen.queryByRole("button", { name: /registrar rango/i })).not.toBeInTheDocument();
  });

  it("con onToggleRangeMode, el botón de rango lo llama al clickear", async () => {
    const onToggle = vi.fn();
    const user = userEvent.setup();
    render(<Odontogram findings={[]} catalog={CATALOG} onToggleRangeMode={onToggle} />);

    await user.click(screen.getByRole("button", { name: /registrar rango/i }));

    expect(onToggle).toHaveBeenCalledOnce();
  });

  it("no interactivo (sellado): la toolbar de botones no se muestra, pero el resumen sí", () => {
    render(<Odontogram findings={[]} catalog={CATALOG} interactive={false} onToggleRangeMode={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "Arco superior" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /registrar rango/i })).not.toBeInTheDocument();
    expect(screen.getByText("hallazgos")).toBeInTheDocument();
  });
});
