import type { KeyboardEvent } from "react";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { teethInQuadrant, type ToothSurfaceCode } from "@/modules/core/utils/fdiTeeth";
import type { FindingFormValues } from "@/modules/dental-chart/schemas/dentalChart";

// ⚠️ Colores NORMATIVOS, no del tema (design.md D10, ADR-0013 D5): NTS 188 fija rojo/azul exactos
// y el odontograma se renderiza siempre sobre fondo blanco con texto oscuro fijo, aunque la app
// esté en modo oscuro — es un documento legal, lo mostrado debe ser idéntico a lo auditado. Única
// excepción admitida a la regla de `frontend-guard` de no hardcodear colores: TODA la paleta (y la
// tipografía, ver `index.html`) de este componente vive acá, nombrada, no dispersa.
const RED = "#FF0000"; // por hacer / patológico — 4.0:1, texto solo en ≥19px bold
const BLUE = "#0000FF"; // realizado / buen estado — 8.6:1
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
 * ⚠️ Simplificación consciente frente al artefacto: cada elemento es su propia parada de Tab
 * (roving tabindex con navegación por flechas queda para una iteración posterior).
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
      (upperTeeth.has(f.location?.toothA ?? 0) ? upperLaneFindings : lowerLaneFindings).push(f);
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

          {lane("SUPERIOR", 4, CHART_WIDTH, upperLaneFindings, colorOf, siglaOfFinding, interactive, selectArch)}

          {ROWS.map((row, i) =>
            toothRow(row, ROW_COL_OFFSET[i], rowY[i], toothFindings, surfaceFindings, colorOf, siglaOfFinding, interactive, selectTooth, selectSurface),
          )}

          {lane("INFERIOR", height - LANE_H + 4, CHART_WIDTH, lowerLaneFindings, colorOf, siglaOfFinding, interactive, selectArch)}
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

function findingColor(f: DentalChartFindingResponse, catalog: DentalFindingCatalogEntryResponse[] | undefined): string {
  const entry = catalog?.find((e) => e.type === f.type && (e.subtype ?? "") === (f.subtype ?? ""));
  if (entry?.colorBehavior === "SIEMPRE_ROJO") return RED;
  if (entry?.colorBehavior === "SIEMPRE_AZUL") return BLUE;
  return f.condition === "POOR" ? RED : BLUE;
}

function siglaOf(f: DentalChartFindingResponse, catalog: DentalFindingCatalogEntryResponse[] | undefined): string {
  const entry = catalog?.find((e) => e.type === f.type && (e.subtype ?? "") === (f.subtype ?? ""));
  return entry?.acronym ?? f.type ?? "";
}

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
        const ringColor = toothList.length > 0 ? colorOf(toothList[toothList.length - 1]) : undefined;

        return (
          <g key={fdi} transform={`translate(${x}, ${y})`}>
            <rect
              x={0}
              y={sigY}
              width={size}
              height={SIGLA_H}
              rx={3}
              fill={PAPER}
              stroke={siglaEntries.length > 0 ? OUTLINE : DIVIDER}
              strokeDasharray={siglaEntries.length > 0 ? undefined : "3 2"}
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

            <g
              role="button"
              tabIndex={0}
              aria-label={toothLabel(fdi, toothList, surfacesOnTooth)}
              onClick={() => selectTooth(fdi)}
              onKeyDown={(e) => activateOnEnterOrSpace(e, () => selectTooth(fdi))}
              className={interactive ? "cursor-pointer focus:outline-none" : "focus:outline-none"}
            >
              <rect x={0} y={numY} width={size} height={NUM_H} rx={3} fill="transparent" />
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
              {ringColor ? (
                <rect x={ring.x} y={ring.y} width={ring.wh} height={ring.wh} rx={4} fill="none" stroke={ringColor} strokeWidth={2.5} pointerEvents="none" />
              ) : null}
            </g>
          </g>
        );
      })}
    </g>
  );
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

/**
 * El carril de un arco (design: "un hallazgo = un elemento"): arco completo como barra de borde a
 * borde, rangos como corchete con sigla, entre-piezas fuera de alcance de este carril simplificado
 * — se anuncian igual por su `aria-label`, apilados si hay más de uno.
 */
function lane(
  arch: "SUPERIOR" | "INFERIOR",
  y: number,
  width: number,
  findings: DentalChartFindingResponse[],
  colorOf: (f: DentalChartFindingResponse) => string,
  siglaOfFinding: (f: DentalChartFindingResponse) => string,
  interactive: boolean,
  selectArch: (arch: "SUPERIOR" | "INFERIOR") => void,
) {
  const archFindings = findings.filter((f) => f.location?.locationKind === "ARCH");
  const otherFindings = findings.filter((f) => f.location?.locationKind !== "ARCH");
  const archColor = archFindings.length > 0 ? colorOf(archFindings[archFindings.length - 1]) : undefined;
  const baseline = arch === "SUPERIOR" ? y + LANE_H - 6 : y + 6;

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
        <rect x={8} y={baseline - 3} width={width - 16} height={6} fill={archColor ?? DIVIDER} />
      </g>

      {otherFindings.map((f, i) => {
        const teeth = f.location?.teeth;
        const label =
          f.location?.locationKind === "TOOTH_RANGE"
            ? `Rango de piezas ${(teeth ?? []).join(", ")}: ${siglaOfFinding(f)}`
            : `Entre las piezas ${f.location?.toothA} y ${f.location?.toothB}: ${siglaOfFinding(f)}`;
        const trackY = arch === "SUPERIOR" ? y + 4 + i * 16 : y + LANE_H - 20 - i * 16;
        return (
          <g key={f.id} role="img" aria-label={label} transform={`translate(8, ${trackY})`}>
            <rect width={110} height={3} fill={colorOf(f)} />
            <text x={116} y={8} fontFamily={FONT_MONO} fontSize={11} fontWeight={700} fill={colorOf(f)}>
              {siglaOfFinding(f)}
            </text>
          </g>
        );
      })}
    </g>
  );
}

function activateOnEnterOrSpace(e: KeyboardEvent<SVGGElement>, action: () => void) {
  if (e.key === "Enter" || e.key === " ") {
    e.preventDefault();
    action();
  }
}
