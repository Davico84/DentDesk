import type { KeyboardEvent, ReactNode } from "react";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { teethInQuadrant, type ToothSurfaceCode } from "@/modules/core/utils/fdiTeeth";
import type { FindingFormValues } from "@/modules/dental-chart/schemas/dentalChart";
import { RED, BLUE, findingColor } from "@/modules/dental-chart/utils/findingColor";

// ⚠️ Colores NORMATIVOS, no del tema (design.md D10, ADR-0013 D5): NTS 188 fija rojo/azul exactos
// y el odontograma se renderiza siempre sobre fondo blanco con texto oscuro fijo, aunque la app
// esté en modo oscuro — es un documento legal, lo mostrado debe ser idéntico a lo auditado. Única
// excepción admitida a la regla de `frontend-guard` de no hardcodear colores: TODA la paleta (y la
// tipografía, ver `index.html`) de este componente vive acá, nombrada, no dispersa — salvo
// RED/BLUE, compartidos con `FindingList` vía `utils/findingColor.ts`.
const PAPER = "#FFFFFF";
const INK = "#16181D"; // texto principal, foco, selección — 17.8:1
const INK_2 = "#4A4F57"; // texto secundario — 8.2:1
const INK_3 = "#6B7079"; // FDI sin hallazgo, rótulos — 5.0:1
const OUTLINE = "#8C929B"; // bordes de superficie — 3.1:1
const DIVIDER = "#D5D8DC"; // recuadro vacío, líneas guía

const FONT_BODY = '"Atkinson Hyperlegible Next", system-ui, sans-serif';
const FONT_MONO = '"Atkinson Hyperlegible Mono", ui-monospace, monospace';

const PITCH = 68;
const MIDLINE_GAP = 20;
const SIGLA_H = 32;
const NUM_H = 18;
const ROW_GAP = 4;
const CROWN_PERM = 56;
const CROWN_TEMP = 44;
const LANE_H = 56;

type LocationUpdate = Partial<FindingFormValues["location"]> & {
  locationKind: FindingFormValues["location"]["locationKind"];
};

type Slot = { surface: ToothSurfaceCode; points: string };

/** La corona: 5 polígonos reales (no un simple cuadrado con cruz), M siempre hacia la línea media
 *  y V siempre hacia afuera — mismo cálculo que la referencia de diseño (Celda.dc.html). */
function crownSlots(fdi: number, size: number): { slots: Slot[]; ring: { x: number; y: number; wh: number } } {
  const q = Math.floor(fdi / 10);
  const upper = q === 1 || q === 2 || q === 5 || q === 6;
  const leftHalf = q === 1 || q === 4 || q === 5 || q === 8;
  const anterior = fdi % 10 <= 3;

  const pad = 4;
  const S = size - pad * 2;
  const a = S * 0.28;
  const y1 = anterior ? S / 2 - S * 0.08 : a;
  const y2 = anterior ? S / 2 + S * 0.08 : S - a;

  const raw: [number, number][][] = [
    [[0, 0], [S, 0], [S - a, y1], [a, y1]],
    [[S, 0], [S, S], [S - a, y2], [S - a, y1]],
    [[a, y2], [S - a, y2], [S, S], [0, S]],
    [[0, 0], [a, y1], [a, y2], [0, S]],
    [[a, y1], [S - a, y1], [S - a, y2], [a, y2]],
  ];
  const surfaces: ToothSurfaceCode[] = [
    upper ? "VESTIBULAR" : "LINGUAL",
    leftHalf ? "MESIAL" : "DISTAL",
    upper ? "PALATINO" : "VESTIBULAR",
    leftHalf ? "DISTAL" : "MESIAL",
    anterior ? "INCISAL" : "OCLUSAL",
  ];
  const slots = raw.map((shape, i) => ({
    surface: surfaces[i],
    points: shape.map(([x, y]) => `${(x + pad).toFixed(1)},${(y + pad).toFixed(1)}`).join(" "),
  }));
  return { slots, ring: { x: 1, y: 1, wh: size - 2 } };
}

const SURFACE_LABEL: Record<ToothSurfaceCode, string> = {
  MESIAL: "mesial",
  DISTAL: "distal",
  VESTIBULAR: "vestibular",
  PALATINO: "palatino",
  LINGUAL: "lingual",
  OCLUSAL: "oclusal",
  INCISAL: "incisal",
};

type Row = { teeth: number[]; upper: boolean; size: number };

const ROWS: Row[] = [
  { teeth: [...teethInQuadrant(1).reverse(), ...teethInQuadrant(2)], upper: true, size: CROWN_PERM },
  { teeth: [...teethInQuadrant(5).reverse(), ...teethInQuadrant(6)], upper: true, size: CROWN_TEMP },
  { teeth: [...teethInQuadrant(8).reverse(), ...teethInQuadrant(7)], upper: false, size: CROWN_TEMP },
  { teeth: [...teethInQuadrant(4).reverse(), ...teethInQuadrant(3)], upper: false, size: CROWN_PERM },
];
// Las temporales se centran bajo el tramo de 16 permanentes (columna 3 a 12).
const ROW_COL_OFFSET = [0, 3, 3, 0];

function colX(i: number): number {
  return i * PITCH + (i >= 8 ? MIDLINE_GAP : 0);
}

const CHART_WIDTH = colX(15) + CROWN_PERM + 4;

/**
 * El odontograma: SVG inline, cada pieza y superficie enfocable/accionable por teclado (design.md
 * D10) — nunca canvas ni imagen. Muestra **ambas denticiones siempre**: el modelo localiza por FDI
 * y temporales/permanentes coexisten sin caso especial (design.md, Risks); no hay un umbral de
 * edad confiable para ocultar una u otra (ADR-0017, INV-15).
 *
 * <p>Dirección visual acordada con el usuario (artefacto de diseño, 2026-09-24): celda de tres
 * capas (sigla · número FDI · corona de 5 polígonos anatómicos), marcadores de arco/rango/entre-
 * piezas en un carril propio fuera de la grilla, paleta y tipografía (Atkinson Hyperlegible) fijas.
 *
 * <p>⚠️ Cada uno de los 38 tipos tiene su propio símbolo en NTS 188 — no son variaciones de 4
 * formas genéricas por alcance. Auditoría completa contra el PDF oficial (2026-09-25, ver memoria
 * "Odontograma: auditoría completa de símbolos"): el anillo alrededor de la corona es **solo**
 * para CORONA/CORONA_TEMPORAL ("cuadrado bordeando la corona"), no un símbolo genérico de pieza —
 * los demás tipos con sigla (impactación, implante, macrodoncia...) van sigla-sola, sin anillo.
 * ⚠️ Simplificación consciente frente al artefacto de diseño: cada elemento es su propia parada de
 * Tab (roving tabindex con navegación por flechas queda para una iteración posterior).
 */
export function Odontogram({
  findings,
  catalog,
  interactive = true,
  onSelectLocation,
}: {
  findings: DentalChartFindingResponse[];
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  /** `false` en un odontograma SELLADO: sigue siendo enfocable/legible, pero no ofrece registrar. */
  interactive?: boolean;
  /** Click en pieza/superficie/arco: dispara {@link import("./FindingDialog").FindingDialog} en
   *  el llamador, con esa localización ya fija — no hay resaltado propio acá porque el modal se
   *  abre en el acto, sin un estado intermedio "seleccionado pero sin acción" que resaltar. */
  onSelectLocation?: (location: LocationUpdate) => void;
}) {
  const colorOf = (f: DentalChartFindingResponse) => findingColor(f, catalog);
  const siglaOfFinding = (f: DentalChartFindingResponse) => siglaOf(f, catalog);

  const toothFindings = new Map<number, DentalChartFindingResponse[]>();
  const surfaceFindings = new Map<string, DentalChartFindingResponse[]>();
  const upperLaneFindings: DentalChartFindingResponse[] = [];
  const lowerLaneFindings: DentalChartFindingResponse[] = [];
  const betweenFindings: DentalChartFindingResponse[] = [];

  const upperTeeth = new Set(ROWS.filter((r) => r.upper).flatMap((r) => r.teeth));

  for (const f of findings) {
    const kind = f.location?.locationKind;
    if (kind === "TOOTH" && f.location?.toothA != null) {
      pushTo(toothFindings, f.location.toothA, f);
    } else if (kind === "SURFACE" && f.location?.toothA != null && f.location.surface) {
      pushTo(surfaceFindings, `${f.location.toothA}-${f.location.surface}`, f);
    } else if (kind === "ARCH") {
      (f.location?.arch === "SUPERIOR" ? upperLaneFindings : lowerLaneFindings).push(f);
    } else if (kind === "BETWEEN_TEETH") {
      // A diferencia de arco/rango, entre-piezas NO va al carril externo: se dibuja pegado a las
      // dos piezas reales que conecta (bug encontrado por el user probando en vivo — quedaba
      // genérico arriba de la grilla, desconectado de dónde está de verdad).
      betweenFindings.push(f);
    } else if (kind === "TOOTH_RANGE") {
      const firstTooth = f.location?.teeth?.[0] ?? 0;
      (upperTeeth.has(firstTooth) ? upperLaneFindings : lowerLaneFindings).push(f);
    }
  }

  function selectTooth(fdi: number) {
    if (!interactive || !onSelectLocation) return;
    onSelectLocation({ locationKind: "TOOTH", toothA: fdi });
  }
  function selectSurface(fdi: number, surface: ToothSurfaceCode) {
    if (!interactive || !onSelectLocation) return;
    onSelectLocation({ locationKind: "SURFACE", toothA: fdi, surface });
  }
  function selectArch(arch: "SUPERIOR" | "INFERIOR") {
    if (!interactive || !onSelectLocation) return;
    onSelectLocation({ locationKind: "ARCH", arch });
  }

  const rowHeights = ROWS.map((r) => SIGLA_H + ROW_GAP + NUM_H + ROW_GAP + r.size);
  const rowsTop = LANE_H;
  const rowY: number[] = [];
  {
    let y = rowsTop;
    for (const h of rowHeights) {
      rowY.push(y);
      y += h;
    }
  }
  const height = rowsTop + rowHeights.reduce((a, b) => a + b, 0) + LANE_H;

  // Centro real de cada pieza (columna × fila), para ubicar entre-piezas y los pilares de un
  // rango exactamente donde corresponde — no en un carril genérico desconectado de las piezas.
  const toothCenters = new Map<number, { cx: number; cy: number }>();
  ROWS.forEach((row, i) => {
    const crownYOffset = row.upper ? SIGLA_H + ROW_GAP + NUM_H + ROW_GAP : 0;
    row.teeth.forEach((fdi, k) => {
      const x = colX(ROW_COL_OFFSET[i] + k);
      toothCenters.set(fdi, { cx: x + row.size / 2, cy: rowY[i] + crownYOffset + row.size / 2 });
    });
  });

  return (
    <div className="w-full overflow-x-auto rounded-2xl border p-4" style={{ background: PAPER, fontFamily: FONT_BODY }}>
      <div className="mb-2 flex justify-between px-14 text-xs" style={{ color: INK_2 }}>
        <span>← Derecha del paciente</span>
        <span>Izquierda del paciente →</span>
      </div>
      <div className="flex">
        <div className="flex shrink-0 flex-col text-right text-[11px] font-bold uppercase tracking-wide" style={{ width: 88 }}>
          <div style={{ height: LANE_H, color: INK_3, display: "flex", alignItems: "center", justifyContent: "flex-end" }}>Rango · arco</div>
          {ROWS.map((r, i) => (
            <div
              key={i}
              style={{ height: rowHeights[i], color: r.size === CROWN_PERM ? INK : INK_2, display: "flex", alignItems: "center", justifyContent: "flex-end" }}
            >
              {r.size === CROWN_PERM ? "Permanente" : "Temporal"}
            </div>
          ))}
          <div style={{ height: LANE_H, color: INK_3, display: "flex", alignItems: "center", justifyContent: "flex-end" }}>Rango · arco</div>
        </div>

        <svg
          role="img"
          aria-label="Odontograma: representación gráfica del estado dental por pieza y superficie"
          viewBox={`0 0 ${CHART_WIDTH} ${height}`}
          width={CHART_WIDTH}
          height={height}
          style={{ background: PAPER, color: INK }}
        >
          <line x1={colX(7) + CROWN_PERM + MIDLINE_GAP / 2} y1={rowsTop} x2={colX(7) + CROWN_PERM + MIDLINE_GAP / 2} y2={height - rowsTop} stroke={DIVIDER} strokeDasharray="2 3" />
          <line x1={0} y1={height / 2} x2={CHART_WIDTH} y2={height / 2} stroke={DIVIDER} />

          {lane("SUPERIOR", 4, CHART_WIDTH, upperLaneFindings, toothCenters, colorOf, siglaOfFinding, interactive, selectArch)}

          {ROWS.map((row, i) =>
            toothRow(row, ROW_COL_OFFSET[i], rowY[i], toothFindings, surfaceFindings, colorOf, siglaOfFinding, interactive, selectTooth, selectSurface),
          )}

          {lane("INFERIOR", height - LANE_H + 4, CHART_WIDTH, lowerLaneFindings, toothCenters, colorOf, siglaOfFinding, interactive, selectArch)}

          {betweenFindings.map((f) => betweenMarker(f, toothCenters, colorOf, siglaOfFinding))}
        </svg>
      </div>

      <div className="mt-4 flex flex-wrap items-start gap-x-8 gap-y-3 text-xs" style={{ color: INK_2 }}>
        <span className="flex items-center gap-1.5">
          <span aria-hidden="true" className="inline-block size-3" style={{ background: RED }} />
          Rojo: por hacer / patológico
        </span>
        <span className="flex items-center gap-1.5">
          <span aria-hidden="true" className="inline-block size-3" style={{ background: BLUE }} />
          Azul: realizado / buen estado
        </span>
        <span>Cada hallazgo lleva además su sigla — el color nunca es el único portador de significado.</span>
        <span>Número FDI en negrita = pieza con al menos un hallazgo.</span>
      </div>
    </div>
  );
}

function pushTo<K>(map: Map<K, DentalChartFindingResponse[]>, key: K, f: DentalChartFindingResponse) {
  const list = map.get(key) ?? [];
  list.push(f);
  map.set(key, list);
}

/**
 * ⚠️ Sin fallback al nombre del tipo (bug real, encontrado por el user probando GEMINACIÓN: el
 * recuadro de sigla es para 2-3 letras, no para "GEMINACION" entero). 17 de los 38 tipos NO
 * tienen sigla oficial — para esos la norma dibuja un símbolo propio (ver `TOOTH_GLYPH` y demás
 * mapas de glifos), no una sigla. Devolver "" es correcto para ellos.
 *
 * <p>⚠️ MOVILIDAD_PATOLOGICA (6.1.19) es la única excepción: su sigla lleva el grado pegado
 * ("M2", no "M" sola) — "la sigla 'M' seguida del número arábigo que representa el grado".
 */
function siglaOf(f: DentalChartFindingResponse, catalog: DentalFindingCatalogEntryResponse[] | undefined): string {
  const entry = catalog?.find((e) => e.type === f.type && (e.subtype ?? "") === (f.subtype ?? ""));
  const acronym = entry?.acronym ?? "";
  if (f.type === "MOVILIDAD_PATOLOGICA" && f.grade != null) return `${acronym}${f.grade}`;
  return acronym;
}

// --- glifos de pieza (TOOTH): auditoría completa contra el PDF oficial, 2026-09-25 ---
// Fuente: nts-188-odontograma-rm-559-2022-ESCANEADA.pdf, numerales citados en cada caso.

type ToothGlyphKind =
  | "ring" // 6.1.3/6.1.4: cuadrado bordeando la corona (SOLO estos dos — no un símbolo genérico de pieza)
  | "numberCircle" // 6.1.12: circunferencia encerrando el número FDI
  | "crossOut" // 6.1.20: aspa sobre la corona (además de la sigla DNE/DEX/DAO en el recuadro)
  | "outsidePost" // 6.1.8: línea vertical + cuadrado, fuera de la corona (hacia la raíz)
  | "outsideTriangle" // 6.1.21: triángulo por encima o debajo de las raíces
  | "outsideArrowOut" // 6.1.24: flecha recta hacia afuera, fuera del gráfico
  | "outsideArrowIn" // 6.1.25: flecha recta hacia el plano oclusal, fuera del gráfico
  | "crownFracture" // 6.1.10: línea representando la fractura sobre la corona/raíz
  | "crownGiroversion" // 6.1.13: flecha curva a nivel de la zona oclusal
  | "crownEruption"; // 6.1.23: flecha en zigzag sobre la gráfica, hacia el plano oclusal

const TOOTH_GLYPH: Record<string, ToothGlyphKind> = {
  CORONA: "ring",
  CORONA_TEMPORAL: "ring",
  GEMINACION: "numberCircle",
  PIEZA_AUSENTE: "crossOut",
  ESPIGO_MUNON: "outsidePost",
  PIEZA_EN_CLAVIJA: "outsideTriangle",
  PIEZA_EXTRUIDA: "outsideArrowOut",
  PIEZA_INTRUIDA: "outsideArrowIn",
  FRACTURA_DENTAL: "crownFracture",
  GIROVERSION: "crownGiroversion",
  PIEZA_EN_ERUPCION: "crownEruption",
};
const OUTSIDE_KINDS = new Set<ToothGlyphKind>(["outsidePost", "outsideTriangle", "outsideArrowOut", "outsideArrowIn"]);
const CROWN_OVERLAY_KINDS = new Set<ToothGlyphKind>(["crownFracture", "crownGiroversion", "crownEruption"]);

function toothRow(
  row: Row,
  colOffset: number,
  y: number,
  toothFindings: Map<number, DentalChartFindingResponse[]>,
  surfaceFindings: Map<string, DentalChartFindingResponse[]>,
  colorOf: (f: DentalChartFindingResponse) => string,
  siglaOfFinding: (f: DentalChartFindingResponse) => string,
  interactive: boolean,
  selectTooth: (fdi: number) => void,
  selectSurface: (fdi: number, surface: ToothSurfaceCode) => void,
) {
  const size = row.size;
  const crownY = row.upper ? SIGLA_H + ROW_GAP + NUM_H + ROW_GAP : 0;
  const numY = row.upper ? SIGLA_H + ROW_GAP : size + ROW_GAP;
  const sigY = row.upper ? 0 : size + ROW_GAP + NUM_H + ROW_GAP;

  return (
    <g key={`row-${y}`}>
      {row.teeth.map((fdi, i) => {
        const x = colX(colOffset + i);
        const { slots, ring } = crownSlots(fdi, size);
        const toothList = toothFindings.get(fdi) ?? [];
        const surfacesOnTooth = slots.flatMap((s) => surfaceFindings.get(`${fdi}-${s.surface}`) ?? []);
        const hasAny = toothList.length > 0 || surfacesOnTooth.length > 0;
        const siglaEntries = uniqueSiglas([...toothList, ...surfacesOnTooth], colorOf, siglaOfFinding);

        const glyphOf = (f: DentalChartFindingResponse) => TOOTH_GLYPH[f.type ?? ""];
        const ringFinding = toothList.find((f) => glyphOf(f) === "ring");
        const numberCircle = toothList.find((f) => glyphOf(f) === "numberCircle");
        const crossOut = toothList.find((f) => glyphOf(f) === "crossOut");
        const outsideFinding = toothList.find((f) => OUTSIDE_KINDS.has(glyphOf(f)));
        const crownOverlay = toothList.find((f) => CROWN_OVERLAY_KINDS.has(glyphOf(f)));

        return (
          <g key={fdi} transform={`translate(${x}, ${y})`}>
            <rect
              x={0}
              y={sigY}
              width={size}
              height={SIGLA_H}
              rx={3}
              fill={PAPER}
              stroke={siglaEntries.length > 0 || outsideFinding ? OUTLINE : DIVIDER}
              strokeDasharray={siglaEntries.length > 0 || outsideFinding ? undefined : "3 2"}
            />
            {siglaEntries.slice(0, 2).map((s, si) => (
              <text
                key={si}
                x={size / 2}
                y={sigY + SIGLA_H / 2 - (siglaEntries.length > 1 ? 8 - si * 16 : 0) + 5}
                textAnchor="middle"
                fontFamily={FONT_MONO}
                fontWeight={700}
                fontSize={size === CROWN_PERM ? 15 : 12}
                fill={s.color}
              >
                {s.text}
              </text>
            ))}
            {outsideFinding && siglaEntries.length === 0
              ? outsideGlyph(glyphOf(outsideFinding), size, sigY, row.upper, colorOf(outsideFinding))
              : null}

            <g
              role="button"
              tabIndex={0}
              aria-label={toothLabel(fdi, toothList, surfacesOnTooth)}
              onClick={() => selectTooth(fdi)}
              onKeyDown={(e) => activateOnEnterOrSpace(e, () => selectTooth(fdi))}
              className={interactive ? "cursor-pointer focus:outline-none" : "focus:outline-none"}
            >
              <rect x={0} y={numY} width={size} height={NUM_H} rx={3} fill="transparent" />
              {numberCircle ? (
                <ellipse
                  cx={size / 2}
                  cy={numY + NUM_H / 2}
                  rx={size === CROWN_PERM ? 15 : 13}
                  ry={NUM_H / 2 + 2}
                  fill="none"
                  stroke={colorOf(numberCircle)}
                  strokeWidth={1.5}
                />
              ) : null}
              <text
                x={size / 2}
                y={numY + NUM_H - 4}
                textAnchor="middle"
                fontFamily={FONT_MONO}
                fontWeight={hasAny ? 700 : 400}
                fontSize={size === CROWN_PERM ? 14 : 12}
                fill={hasAny ? INK : INK_3}
              >
                {fdi}
              </text>
            </g>

            <g transform={`translate(0, ${crownY})`}>
              {slots.map((slot) => {
                const list = surfaceFindings.get(`${fdi}-${slot.surface}`) ?? [];
                const fill = list.length > 0 ? colorOf(list[list.length - 1]) : PAPER;
                return (
                  <polygon
                    key={slot.surface}
                    role="button"
                    tabIndex={0}
                    aria-label={`Pieza ${fdi}, superficie ${SURFACE_LABEL[slot.surface]}${list.length > 0 ? `: ${list.length} hallazgo(s)` : ", sin hallazgos"}`}
                    onClick={() => selectSurface(fdi, slot.surface)}
                    onKeyDown={(e) => activateOnEnterOrSpace(e, () => selectSurface(fdi, slot.surface))}
                    className={interactive ? "cursor-pointer focus:outline-none" : "focus:outline-none"}
                    points={slot.points}
                    fill={fill}
                    stroke={OUTLINE}
                    strokeWidth={1}
                    strokeLinejoin="round"
                  />
                );
              })}
              {ringFinding ? (
                <rect x={ring.x} y={ring.y} width={ring.wh} height={ring.wh} rx={4} fill="none" stroke={colorOf(ringFinding)} strokeWidth={2.5} pointerEvents="none" />
              ) : null}
              {crossOut ? (
                // 6.1.20: aspa de color azul sobre la corona, además de la sigla en el recuadro.
                <g pointerEvents="none" stroke={colorOf(crossOut)} strokeWidth={2}>
                  <line x1={4} y1={4} x2={size - 4} y2={size - 4} />
                  <line x1={size - 4} y1={4} x2={4} y2={size - 4} />
                </g>
              ) : null}
              {crownOverlay ? crownOverlayGlyph(glyphOf(crownOverlay), size, colorOf(crownOverlay)) : null}
            </g>
          </g>
        );
      })}
    </g>
  );
}

/** Los 4 glifos "fuera de la corona" (hacia la raíz / fuera del gráfico): comparten posición en
 *  el recuadro de sigla — nunca sobre la corona, donde se confundían con una cara de superficie
 *  (bug real, espigo-muñón, encontrado por el user probando en vivo). */
function outsideGlyph(kind: ToothGlyphKind, size: number, sigY: number, upper: boolean, color: string): ReactNode {
  const cx = size / 2;
  const outerY = sigY + 4; // el borde del recuadro más lejos de la corona
  const innerY = sigY + SIGLA_H - 6; // el borde más cerca de la corona

  switch (kind) {
    case "outsidePost":
      // 6.1.8: línea vertical unida a un cuadrado.
      return (
        <g key="glyph" pointerEvents="none" stroke={color}>
          <line x1={cx} y1={outerY + 4} x2={cx} y2={innerY} strokeWidth={2} />
          <rect x={cx - 4} y={outerY} width={8} height={8} fill="none" strokeWidth={1.5} />
        </g>
      );
    case "outsideTriangle": {
      // 6.1.21: triángulo por encima (fila superior) o debajo (fila inferior) de las raíces.
      const apexY = upper ? outerY : innerY;
      const baseY = upper ? innerY : outerY;
      return (
        <polygon
          key="glyph"
          pointerEvents="none"
          points={`${cx},${apexY} ${cx - 6},${baseY} ${cx + 6},${baseY}`}
          fill="none"
          stroke={color}
          strokeWidth={1.5}
        />
      );
    }
    case "outsideArrowOut":
      // 6.1.24: flecha hacia afuera del gráfico (se aleja de la corona — extruida).
      return arrow(cx, innerY, cx, outerY, color);
    case "outsideArrowIn":
      // 6.1.25: flecha hacia el plano oclusal (entra hacia la corona — intruida).
      return arrow(cx, outerY, cx, innerY, color);
    default:
      return null;
  }
}

function arrow(x1: number, y1: number, x2: number, y2: number, color: string): ReactNode {
  const up = y2 < y1;
  return (
    <g key="glyph" pointerEvents="none" stroke={color} strokeWidth={2}>
      <line x1={x1} y1={y1} x2={x2} y2={y2} />
      <polyline points={up ? `${x2 - 4},${y2 + 5} ${x2},${y2} ${x2 + 4},${y2 + 5}` : `${x2 - 4},${y2 - 5} ${x2},${y2} ${x2 + 4},${y2 - 5}`} fill="none" />
    </g>
  );
}

/** Los 3 glifos que sí van "sobre la figura de la corona" (fractura, giroversión, erupción) —
 *  trazos finos que cruzan la corona entera, nunca un relleno: no se confunden con una cara de
 *  superficie porque no ocupan la forma de ninguna cara específica. */
function crownOverlayGlyph(kind: ToothGlyphKind, size: number, color: string): ReactNode {
  const c = size / 2;
  switch (kind) {
    case "crownFracture":
      // 6.1.10: línea representando la fractura sobre la corona y/o la raíz.
      return <line key="glyph" x1={size * 0.2} y1={size * 0.15} x2={size * 0.8} y2={size * 0.85} stroke={color} strokeWidth={2} pointerEvents="none" />;
    case "crownGiroversion":
      // 6.1.13: flecha curva a nivel de la zona oclusal.
      return (
        <g key="glyph" pointerEvents="none">
          <path d={`M ${c - 10} ${c + 6} A 12 12 0 1 1 ${c + 6} ${c - 10}`} fill="none" stroke={color} strokeWidth={2} />
          <polyline points={`${c + 2},${c - 14} ${c + 6},${c - 10} ${c + 1},${c - 6}`} fill="none" stroke={color} strokeWidth={2} />
        </g>
      );
    case "crownEruption":
      // 6.1.23: flecha en zigzag dirigida hacia el plano oclusal (hacia el centro).
      return (
        <g key="glyph" pointerEvents="none" stroke={color} strokeWidth={2} fill="none">
          <polyline points={`${c - 6},${size - 8} ${c + 6},${size - 18} ${c - 6},${size - 28} ${c},${8}`} />
          <polyline points={`${c - 4},${13} ${c},${8} ${c + 4},${13}`} />
        </g>
      );
    default:
      return null;
  }
}

function uniqueSiglas(
  list: DentalChartFindingResponse[],
  colorOf: (f: DentalChartFindingResponse) => string,
  siglaOfFinding: (f: DentalChartFindingResponse) => string,
): { text: string; color: string }[] {
  const seen = new Set<string>();
  const out: { text: string; color: string }[] = [];
  for (const f of list) {
    const text = siglaOfFinding(f);
    if (!text) continue; // sin sigla oficial: su símbolo es otro (ver TOOTH_GLYPH), no un recuadro vacío
    const color = colorOf(f);
    const key = text + color;
    if (!seen.has(key)) {
      seen.add(key);
      out.push({ text, color });
    }
  }
  if (out.length > 2) {
    return [out[0], { text: `+${out.length - 1}`, color: INK }];
  }
  return out;
}

function toothLabel(fdi: number, list: DentalChartFindingResponse[], surfaces: DentalChartFindingResponse[]): string {
  const parts = [`Pieza ${fdi}`];
  if (list.length > 0) parts.push(`${list.length} hallazgo(s) de pieza`);
  if (surfaces.length > 0) parts.push(`${surfaces.length} hallazgo(s) de superficie`);
  if (list.length === 0 && surfaces.length === 0) parts.push("sin hallazgos");
  return parts.join(", ");
}

// --- glifos de arco y rango: cada tipo tiene el suyo, no son intercambiables ---

type ArchGlyphKind = "singleLine" | "doubleLine" | "zigzag";
const ARCH_GLYPH: Record<string, ArchGlyphKind> = {
  EDENTULO_TOTAL: "singleLine", // 6.1.7: línea recta horizontal sobre las coronas
  PROTESIS_COMPLETA: "doubleLine", // 6.1.30: 2 líneas rectas paralelas y horizontales
  APARATO_ORTODONTICO_REMOVIBLE: "zigzag", // 6.1.2: línea en zig-zag a la altura de los ápices
};

type RangeGlyphKind = "bracketPillars" | "doubleLine" | "squaresWithCross";
const RANGE_GLYPH: Record<string, RangeGlyphKind> = {
  PROTESIS_PARCIAL_FIJA: "bracketPillars", // 6.1.29: línea horizontal con verticales sobre los pilares
  PROTESIS_PARCIAL_REMOVIBLE: "doubleLine", // 6.1.31: 2 líneas horizontales paralelas
  APARATO_ORTODONTICO_FIJO: "squaresWithCross", // 6.1.1: cuadrados con una cruz en los extremos, unidos por una línea
};

/**
 * El carril de un arco: arco completo y rango, cada tipo con su propio glifo (auditoría completa
 * 2026-09-25) — un aparato removible NO se ve igual que una prótesis completa, aunque ambos sean
 * de arco. Entre-piezas queda fuera de este carril (ver `betweenMarker`).
 */
function lane(
  arch: "SUPERIOR" | "INFERIOR",
  y: number,
  width: number,
  findings: DentalChartFindingResponse[],
  toothCenters: Map<number, { cx: number; cy: number }>,
  colorOf: (f: DentalChartFindingResponse) => string,
  siglaOfFinding: (f: DentalChartFindingResponse) => string,
  interactive: boolean,
  selectArch: (arch: "SUPERIOR" | "INFERIOR") => void,
) {
  const archFindings = findings.filter((f) => f.location?.locationKind === "ARCH");
  const rangeFindings = findings.filter((f) => f.location?.locationKind === "TOOTH_RANGE");
  const lastArch = archFindings[archFindings.length - 1];
  // La barra de SUPERIOR quedaba pegada (a veces solapada) con la primera fila de piezas — el
  // user lo notó probando en vivo. Más margen antes de la fila (INFERIOR ya tenía suficiente).
  const baseline = arch === "SUPERIOR" ? y + LANE_H - 14 : y + 6;

  return (
    <g key={arch}>
      <g
        role="button"
        tabIndex={0}
        aria-label={`Arco ${arch.toLowerCase()}${archFindings.length > 0 ? `: ${archFindings.map(siglaOfFinding).join(", ")}` : ", sin hallazgos de arco completo"}`}
        onClick={() => selectArch(arch)}
        onKeyDown={(e) => activateOnEnterOrSpace(e, () => selectArch(arch))}
        className={interactive ? "cursor-pointer focus:outline-none" : "focus:outline-none"}
      >
        {lastArch ? (
          archGlyph(ARCH_GLYPH[lastArch.type ?? ""], 8, width - 8, baseline, colorOf(lastArch))
        ) : (
          <rect x={8} y={baseline - 1} width={width - 16} height={2} fill={DIVIDER} />
        )}
      </g>

      {rangeFindings.map((f, i) => {
        const teeth = f.location?.teeth ?? [];
        const label = `Rango de piezas ${teeth.join(", ")}: ${siglaOfFinding(f)}`;
        const trackY = arch === "SUPERIOR" ? y + 6 + i * 14 : y + LANE_H - 20 - i * 14;
        const first = teeth[0] != null ? toothCenters.get(teeth[0]) : undefined;
        const last = teeth[teeth.length - 1] != null ? toothCenters.get(teeth[teeth.length - 1]) : undefined;
        const x1 = first?.cx ?? 8;
        const x2 = last?.cx ?? 118;
        return (
          <g key={f.id} role="img" aria-label={label}>
            {rangeGlyph(RANGE_GLYPH[f.type ?? ""], Math.min(x1, x2), Math.max(x1, x2), trackY, colorOf(f))}
            <text x={Math.min(x1, x2)} y={trackY - 4} fontFamily={FONT_MONO} fontSize={10} fontWeight={700} fill={colorOf(f)}>
              {siglaOfFinding(f)}
            </text>
          </g>
        );
      })}
    </g>
  );
}

function archGlyph(kind: ArchGlyphKind | undefined, x1: number, x2: number, y: number, color: string): ReactNode {
  switch (kind) {
    case "doubleLine":
      return (
        <g stroke={color} strokeWidth={2}>
          <line x1={x1} y1={y - 3} x2={x2} y2={y - 3} />
          <line x1={x1} y1={y + 3} x2={x2} y2={y + 3} />
        </g>
      );
    case "zigzag": {
      const step = (x2 - x1) / 16;
      const points = Array.from({ length: 17 }, (_, i) => `${x1 + i * step},${y + (i % 2 === 0 ? -4 : 4)}`).join(" ");
      return <polyline points={points} fill="none" stroke={color} strokeWidth={2} />;
    }
    case "singleLine":
    default:
      return <line x1={x1} y1={y} x2={x2} y2={y} stroke={color} strokeWidth={3} />;
  }
}

function rangeGlyph(kind: RangeGlyphKind | undefined, x1: number, x2: number, y: number, color: string): ReactNode {
  switch (kind) {
    case "doubleLine":
      return (
        <g stroke={color} strokeWidth={2}>
          <line x1={x1} y1={y - 2} x2={x2} y2={y - 2} />
          <line x1={x1} y1={y + 2} x2={x2} y2={y + 2} />
        </g>
      );
    case "squaresWithCross":
      return (
        <g stroke={color} strokeWidth={1.5}>
          <line x1={x1} y1={y} x2={x2} y2={y} />
          {[x1, x2].map((x, i) => (
            <g key={i}>
              <rect x={x - 5} y={y - 5} width={10} height={10} fill="none" />
              <line x1={x - 5} y1={y - 5} x2={x + 5} y2={y + 5} />
              <line x1={x + 5} y1={y - 5} x2={x - 5} y2={y + 5} />
            </g>
          ))}
        </g>
      );
    case "bracketPillars":
    default:
      return (
        <g stroke={color} strokeWidth={1.5}>
          <line x1={x1} y1={y} x2={x2} y2={y} />
          <line x1={x1} y1={y - 5} x2={x1} y2={y + 5} />
          <line x1={x2} y1={y - 5} x2={x2} y2={y + 5} />
        </g>
      );
  }
}

// --- glifos entre-piezas: cada tipo tiene el suyo (auditoría completa 2026-09-25) ---

type BetweenGlyphKind = "circleSigla" | "invertedParen" | "doubleCircle" | "crossedArrows";
const BETWEEN_GLYPH: Record<string, BetweenGlyphKind> = {
  PIEZA_SUPERNUMERARIA: "circleSigla", // 6.1.26: circunferencia con la sigla S encerrada
  DIASTEMA: "invertedParen", // 6.1.6: signo del paréntesis invertido
  FUSION: "doubleCircle", // 6.1.11: 2 circunferencias interceptadas, una por pieza
  TRANSPOSICION_DENTARIA: "crossedArrows", // 6.1.38: 2 flechas curvas entrecruzadas
};

/**
 * Entre-piezas: cada tipo con su propio símbolo, verificado contra el PDF oficial — no son todos
 * "un círculo con sigla" (eso es solo supernumeraria, 6.1.26). Se posiciona con el centro real de
 * cada pieza, no con un carril genérico.
 */
function betweenMarker(
  f: DentalChartFindingResponse,
  toothCenters: Map<number, { cx: number; cy: number }>,
  colorOf: (f: DentalChartFindingResponse) => string,
  siglaOfFinding: (f: DentalChartFindingResponse) => string,
) {
  const a = f.location?.toothA != null ? toothCenters.get(f.location.toothA) : undefined;
  const b = f.location?.toothB != null ? toothCenters.get(f.location.toothB) : undefined;
  if (!a || !b) return null;
  const cx = (a.cx + b.cx) / 2;
  const cy = (a.cy + b.cy) / 2;
  const color = colorOf(f);
  const sigla = siglaOfFinding(f);
  const label = `Entre las piezas ${f.location?.toothA} y ${f.location?.toothB}: ${sigla || f.type}`;
  const kind = BETWEEN_GLYPH[f.type ?? ""] ?? "circleSigla";

  return (
    <g key={f.id} role="img" aria-label={label}>
      {kind === "invertedParen" ? (
        // 6.1.6: signo del paréntesis invertido — )( entre las dos piezas.
        <g stroke={color} strokeWidth={2} fill="none">
          <path d={`M ${cx - 6} ${cy - 8} Q ${cx - 1} ${cy} ${cx - 6} ${cy + 8}`} />
          <path d={`M ${cx + 6} ${cy - 8} Q ${cx + 1} ${cy} ${cx + 6} ${cy + 8}`} />
        </g>
      ) : kind === "doubleCircle" ? (
        // 6.1.11: 2 circunferencias interceptadas, encerrando el número de cada pieza.
        <g stroke={color} strokeWidth={1.5} fill="none">
          <circle cx={a.cx} cy={a.cy} r={12} />
          <circle cx={b.cx} cy={b.cy} r={12} />
        </g>
      ) : kind === "crossedArrows" ? (
        // 6.1.38: 2 flechas curvas entrecruzadas, a la altura de los números.
        <g stroke={color} strokeWidth={2} fill="none">
          <path d={`M ${a.cx - 6} ${a.cy + 6} Q ${cx} ${cy - 10} ${b.cx + 6} ${b.cy + 6}`} />
          <path d={`M ${a.cx - 6} ${a.cy - 6} Q ${cx} ${cy + 10} ${b.cx + 6} ${b.cy - 6}`} />
        </g>
      ) : (
        <>
          <circle cx={cx} cy={cy} r={10} fill={PAPER} stroke={color} strokeWidth={2} />
          <text x={cx} y={cy + 4} textAnchor="middle" fontFamily={FONT_MONO} fontSize={11} fontWeight={700} fill={color}>
            {sigla}
          </text>
        </>
      )}
    </g>
  );
}

function activateOnEnterOrSpace(e: KeyboardEvent<SVGGElement>, action: () => void) {
  if (e.key === "Enter" || e.key === " ") {
    e.preventDefault();
    action();
  }
}
