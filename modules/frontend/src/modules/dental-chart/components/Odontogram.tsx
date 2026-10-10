import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { ArrowLeftRight } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { teethInQuadrant, type ToothSurfaceCode } from "@/modules/core/utils/fdiTeeth";
import type { FindingFormValues } from "@/modules/dental-chart/schemas/dentalChart";
import { RED, BLUE, findingColor } from "@/modules/dental-chart/utils/findingColor";
import { HintTooltip } from "@/modules/core/components/HintTooltip";
import { useHorizontalOverflow } from "@/modules/core/hooks/useHorizontalOverflow";
import { FindingsSummary } from "./FindingsSummary";
import { RangeModeHint } from "./RangeModeHint";
import {
  PAPER, INK, INK_2, INK_3, OUTLINE, DIVIDER, SELECTION, FONT_BODY, FONT_MONO,
} from "@/modules/dental-chart/utils/chartPalette";

const PITCH = 68;
const MIDLINE_GAP = 20;
// Alto: solo márgenes y separaciones verticales se recortaron (dos pasadas de −10% a pedido del
// user, 496→416 sin rangos). Coronas, ancho y tamaños de letra NO: es un documento legal, tiene que
// seguir legible. Esto es el piso: SIGLA_H más bajo ya pega dos siglas apiladas (sellante + caries
// en la misma pieza, ADR-0017 D7); LANE_H más bajo corta el zig-zag de arco (±4 de su línea).
const SIGLA_H = 28; // dos siglas apiladas a ±7 del centro, ~3px de aire entre ellas
const SIGLA_STACK_OFFSET = 7;
const NUM_H = 15;
const ROW_GAP = 1;
const CROWN_PERM = 56;
const CROWN_TEMP = 44;
const LANE_H = 16; // alto del carril de ARCO (bar/botón) — nunca comparte espacio con rangos
// ⚠️ CADA fila (las 4) tiene su propia franja de rango, pegada a ella — nunca el carril de arco.
// Primera versión (solo filas 1/2, temporales) hacía compartir el carril de arco con las filas 0/3
// (permanentes) — con 2-3 rangos concurrentes en la misma fila (caso real: aparato fijo + prótesis
// parcial fija + prótesis parcial removible, las 3 en la fila permanente superior a la vez), el
// tercero quedaba pegado a la corona o al propio carril de arco (bug real, probado en vivo,
// "pegada al código de un hallazgo", "sobre la línea gris" — dos veces, primero en fila temporal,
// después en fila permanente: el problema era genérico, no exclusivo de una fila). Unificado: las
// 4 filas usan el mismo patrón, el carril de arco queda libre solo para el bar/botón de arco.
//
// ⚠️ Un alto FIJO por franja (probado con 48px y 72px) sobraba en el caso común (0-1 rangos, la
// gran mayoría) y el user lo notó como espacio en blanco sin uso ("¿para qué me sirve este
// espacio?", probado en vivo) — cada franja mide ahora según cuántos carriles necesita de verdad
// (`rangeStripHeight`, a partir de `packRangeLanes`), no un tamaño fijo que sobra o falta.
const RANGE_STACK_STEP = 16;

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
/** Columna de rótulos (Permanente / Temporal) a la izquierda del dibujo. */
const LABEL_W = 88;
/**
 * Ancho mínimo del dibujo (rótulos + piezas), el que ya tiene en tablet: más angosto no se encoge,
 * se desliza dentro de su tarjeta (`update-responsive-layout` design D1). A 600 px las coronas
 * miden ~26 px; a 375 px sin este mínimo medían ~8 px y no se podían leer ni tocar.
 */
const CHART_MIN_WIDTH = 600;

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
  rangeMode = false,
  onToggleRangeMode,
  onSelectLocation,
}: {
  findings: DentalChartFindingResponse[];
  catalog: DentalFindingCatalogEntryResponse[] | undefined;
  /** `false` en un odontograma SELLADO: sigue siendo enfocable/legible, pero no ofrece registrar. */
  interactive?: boolean;
  /** El estado de "modo rango" vive en el llamador (junto con `onToggleRangeMode`, no acá) porque
   *  sobrevive a un remount de este componente si algún día hiciera falta — acá solo se lee. Con
   *  `true`, clickear una pieza ya no abre el modal directo — ancla el inicio del rango (resaltado
   *  con {@link SELECTION}) y espera un segundo click, en la misma fila, para cerrarlo. A
   *  diferencia del resto de alcances, este SÍ necesita un estado intermedio visible porque son
   *  dos clicks, no uno. */
  rangeMode?: boolean;
  /** Toolbar propia (botón "Registrar rango" + "Arco superior/inferior"), visible solo si
   *  `interactive` — el user pidió que dejara de sentirse "ajena" al odontograma, viviendo afuera
   *  en la pantalla que lo aloja. Sin este callback, el botón de rango no se muestra (no tiene
   *  sentido sin poder togglear el estado que el llamador posee). */
  onToggleRangeMode?: () => void;
  /** Click en pieza/superficie/arco/rango/entre-piezas: dispara
   *  {@link import("./FindingDialog").FindingDialog} en el llamador, con esa localización ya fija
   *  — no hay resaltado propio para un click único porque el modal se abre en el acto, sin un
   *  estado intermedio que resaltar (la excepción es `rangeMode`, ver arriba). */
  onSelectLocation?: (location: LocationUpdate) => void;
}) {
  const colorOf = (f: DentalChartFindingResponse) => findingColor(f, catalog);
  const siglaOfFinding = (f: DentalChartFindingResponse) => siglaOf(f, catalog);
  const [rangeStart, setRangeStart] = useState<number | null>(null);
  // Ajuste de estado derivado durante el render (no en un efecto): si el llamador apaga
  // rangeMode a mitad de selección, el ancla no debe sobrevivir a la próxima vez que lo prenda.
  const [prevRangeMode, setPrevRangeMode] = useState(rangeMode);
  const scrollRef = useRef<HTMLDivElement>(null);
  const overflow = useHorizontalOverflow(scrollRef);
  if (rangeMode !== prevRangeMode) {
    setPrevRangeMode(rangeMode);
    if (!rangeMode) setRangeStart(null);
  }

  useEffect(() => {
    if (!rangeMode || rangeStart == null) return;
    function onKeyDown(e: globalThis.KeyboardEvent) {
      if (e.key === "Escape") setRangeStart(null);
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [rangeMode, rangeStart]);

  const toothFindings = new Map<number, DentalChartFindingResponse[]>();
  const surfaceFindings = new Map<string, DentalChartFindingResponse[]>();
  const upperLaneFindings: DentalChartFindingResponse[] = [];
  const lowerLaneFindings: DentalChartFindingResponse[] = [];
  const betweenFindings: DentalChartFindingResponse[] = [];
  // Un rango se dibuja junto a SU fila real, no un binario arriba/abajo (bug real: un rango con
  // piezas temporales caía en el carril de arriba de TODO, lejos de la fila 54-51 que describía).
  const rangesByRow: DentalChartFindingResponse[][] = ROWS.map(() => []);

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
      const rowIndex = ROWS.findIndex((r) => r.teeth.includes(firstTooth));
      if (rowIndex >= 0) rangesByRow[rowIndex].push(f);
    }
  }

  function selectTooth(fdi: number) {
    if (!interactive || !onSelectLocation) return;
    if (rangeMode) {
      if (rangeStart == null) {
        setRangeStart(fdi);
        return;
      }
      // El rango exige dos piezas de la MISMA fila (norma: prótesis/aparato sobre piezas
      // contiguas). Si el segundo click no forma un rango válido con el ancla, no es un error —
      // re-ancla ahí mismo, para no dejar al user en un callejón sin salida.
      const row = ROWS.find((r) => r.teeth.includes(rangeStart) && r.teeth.includes(fdi));
      if (!row || rangeStart === fdi) {
        setRangeStart(fdi);
        return;
      }
      const i1 = row.teeth.indexOf(rangeStart);
      const i2 = row.teeth.indexOf(fdi);
      const teeth = row.teeth.slice(Math.min(i1, i2), Math.max(i1, i2) + 1);
      onSelectLocation({ locationKind: "TOOTH_RANGE", teeth });
      setRangeStart(null);
      return;
    }
    onSelectLocation({ locationKind: "TOOTH", toothA: fdi });
  }
  function selectSurface(fdi: number, surface: ToothSurfaceCode) {
    if (!interactive || !onSelectLocation || rangeMode) return;
    onSelectLocation({ locationKind: "SURFACE", toothA: fdi, surface });
  }
  function selectArch(arch: "SUPERIOR" | "INFERIOR") {
    if (!interactive || !onSelectLocation || rangeMode) return;
    onSelectLocation({ locationKind: "ARCH", arch });
  }
  function selectBetween(toothA: number, toothB: number) {
    if (!interactive || !onSelectLocation || rangeMode) return;
    onSelectLocation({ locationKind: "BETWEEN_TEETH", toothA, toothB });
  }

  const rowHeights = ROWS.map((r) => SIGLA_H + ROW_GAP + NUM_H + ROW_GAP + r.size);
  // Rótulo "Permanente"/"Temporal" pegado a la corona de SU fila, como en el artefacto de diseño
  // — no centrado en toda la fila (bug real: en las filas superiores, con sigla+número ANTES de la
  // corona, quedaba flotando lejos de las piezas; en las inferiores, con la corona primero, casi
  // las tocaba — misma alineación "arriba" daba dos resultados opuestos, el user lo notó como
  // "sin simetría"). Las filas superiores dibujan sigla+número antes de la corona; las inferiores,
  // la corona primero — el rótulo se ancla siempre un poco DENTRO de la corona, no en su borde.
  const UPPER_LABEL_PAD = SIGLA_H + ROW_GAP + NUM_H + ROW_GAP + 12;
  const LOWER_LABEL_PAD = 12;

  // Posición horizontal de cada pieza — no depende de en qué fila (Y) cae, así que se puede
  // calcular ANTES del layout vertical, para saber cuántos carriles necesita cada franja de rango
  // (`packRangeLanes`) y no reservarle un alto fijo que la mayoría de las veces sobra.
  const toothX = new Map<number, number>();
  ROWS.forEach((row, i) => {
    row.teeth.forEach((fdi, k) => toothX.set(fdi, colX(ROW_COL_OFFSET[i] + k) + row.size / 2));
  });
  const rangeLanes = rangesByRow.map((g) => packRangeLanes(g, (fdi) => toothX.get(fdi)));
  const rangeStripHeights = rangeLanes.map((l) => rangeStripHeight(l.laneCount));

  // Carril de arco (LANE_H) → franja de rango de la fila 0 → fila 0 → franja de la fila 1 → fila 1
  // → fila 2 (pegada a la fila 1, sin franja: es la línea media entre denticiones) → franja de la
  // fila 2 → fila 3 → franja de la fila 3 → carril de arco. Las 4 filas tienen su propia franja,
  // ninguna comparte espacio con el carril de arco (ver comentario junto a LANE_H, arriba).
  const rowsTop = LANE_H + rangeStripHeights[0];
  const rowY: number[] = [];
  {
    let y = rowsTop;
    for (let i = 0; i < rowHeights.length; i++) {
      if (i === 1) y += rangeStripHeights[1];
      if (i === 3) y += rangeStripHeights[2];
      rowY.push(y);
      y += rowHeights[i];
    }
  }
  const height = rowY[3] + rowHeights[3] + rangeStripHeights[3] + LANE_H;
  const rangeStrips = [
    { top: rowY[0] - rangeStripHeights[0], bottom: rowY[0] }, // fila 0 (permanente superior)
    { top: rowY[0] + rowHeights[0], bottom: rowY[1] }, // fila 1 (temporal superior)
    { top: rowY[2] + rowHeights[2], bottom: rowY[3] }, // fila 2 (temporal inferior)
    { top: rowY[3] + rowHeights[3], bottom: rowY[3] + rowHeights[3] + rangeStripHeights[3] }, // fila 3 (permanente inferior)
  ];

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
    <div className="w-full rounded-2xl border px-4 py-3" style={{ background: PAPER, fontFamily: FONT_BODY }}>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-3">
        {interactive ? (
          <div className="flex flex-wrap items-center gap-2">
            {(["SUPERIOR", "INFERIOR"] as const).map((arch) => {
              const label = arch === "SUPERIOR" ? "Arco superior" : "Arco inferior";
              const arcada = arch === "SUPERIOR" ? "superior" : "inferior";
              return (
                <HintTooltip
                  key={arch}
                  triggerDisabled={rangeMode}
                  content={
                    rangeMode
                      ? "No disponible mientras registras un rango. Termina o cancela el rango primero."
                      : `Registra un hallazgo que abarca toda la arcada ${arcada}: edéntulo total, prótesis completa o aparato ortodóntico removible.`
                  }
                >
                  <Button type="button" size="sm" variant="outline" disabled={rangeMode} onClick={() => selectArch(arch)}>
                    {label}
                  </Button>
                </HintTooltip>
              );
            })}
            {onToggleRangeMode ? (
              <HintTooltip
                content={
                  rangeMode
                    ? "Sale del modo rango sin registrar nada."
                    : "Para hallazgos sobre varias piezas contiguas de una misma fila (prótesis parcial fija, aparato ortodóntico fijo). Después eliges la primera y la última pieza."
                }
              >
                <Button
                  type="button"
                  size="sm"
                  variant={rangeMode ? "default" : "outline"}
                  aria-pressed={rangeMode}
                  onClick={onToggleRangeMode}
                >
                  <ArrowLeftRight className="mr-2 size-4" />
                  {rangeMode ? "Cancelar rango" : "Registrar rango"}
                </Button>
              </HintTooltip>
            ) : null}
          </div>
        ) : null}
        <FindingsSummary findings={findings} catalog={catalog} />
      </div>
      {interactive && rangeMode ? <RangeModeHint rangeStart={rangeStart} /> : null}
      <div className="mb-1 flex justify-between px-14 text-xs" style={{ color: INK_2 }}>
        <span>← Derecha del paciente</span>
        <span>Izquierda del paciente →</span>
      </div>
      {/* Solo el dibujo se desliza; barra de herramientas, orientación y leyenda quedan a la vista. */}
      <div className="relative">
        <div ref={scrollRef} data-testid="odontogram-scroll" className="overflow-x-auto">
          <div className="flex" style={{ minWidth: CHART_MIN_WIDTH, maxWidth: LABEL_W + CHART_WIDTH }}>
            {/* Los rótulos se ubican en % del alto del dibujo, que escala con su ancho: así quedan
                a la altura de su fila a cualquier ancho, sin franjas vacías (design D2). Su letra
                no escala, para que a 600 px se siga leyendo. `sticky`: al deslizar, siguen a la vista. */}
            <div
              className="sticky left-0 z-10 shrink-0 self-stretch text-[11px] font-bold uppercase tracking-wide"
              style={{ width: LABEL_W, background: PAPER }}
            >
              {[
                { top: rowY[0] + UPPER_LABEL_PAD, text: "Permanente", color: INK },
                { top: rowY[1] + UPPER_LABEL_PAD, text: "Temporal", color: INK_2 },
                { top: rowY[2] + LOWER_LABEL_PAD, text: "Temporal", color: INK_2 },
                { top: rowY[3] + LOWER_LABEL_PAD, text: "Permanente", color: INK },
              ].map((label, i) => (
                <div key={i} className="absolute left-0" style={{ top: `${(label.top / height) * 100}%`, color: label.color }}>
                  {label.text}
                </div>
              ))}
            </div>

            <svg
              role="img"
              aria-label="Odontograma: representación gráfica del estado dental por pieza y superficie"
              viewBox={`0 0 ${CHART_WIDTH} ${height}`}
              width="100%"
              className="block h-auto min-w-0 flex-1"
              style={{ background: PAPER, color: INK, maxWidth: CHART_WIDTH }}
            >
              <line x1={colX(7) + CROWN_PERM + MIDLINE_GAP / 2} y1={LANE_H} x2={colX(7) + CROWN_PERM + MIDLINE_GAP / 2} y2={height - LANE_H} stroke={DIVIDER} strokeDasharray="2 3" />
              <line x1={0} y1={rowY[2]} x2={CHART_WIDTH} y2={rowY[2]} stroke={DIVIDER} />

              {lane("SUPERIOR", 4, CHART_WIDTH, upperLaneFindings, colorOf, siglaOfFinding, interactive && !rangeMode, selectArch)}

              {rangesByRow[0].length > 0
                ? rangeMarkers(rangesByRow[0], rangeLanes[0], (i) => rangeStrips[0].bottom - 16 - i * RANGE_STACK_STEP, toothCenters, colorOf, siglaOfFinding)
                : null}

              {rangesByRow[1].length > 0
                ? rangeMarkers(rangesByRow[1], rangeLanes[1], (i) => rangeStrips[1].bottom - 16 - i * RANGE_STACK_STEP, toothCenters, colorOf, siglaOfFinding)
                : null}

              {ROWS.map((row, i) =>
                toothRow(
                  row,
                  ROW_COL_OFFSET[i],
                  rowY[i],
                  toothFindings,
                  surfaceFindings,
                  colorOf,
                  siglaOfFinding,
                  interactive,
                  interactive && !rangeMode,
                  selectTooth,
                  selectSurface,
                  rangeStart,
                  selectBetween,
                ),
              )}

              {rangesByRow[2].length > 0
                ? rangeMarkers(rangesByRow[2], rangeLanes[2], (i) => rangeStrips[2].top + 16 + i * RANGE_STACK_STEP, toothCenters, colorOf, siglaOfFinding)
                : null}

              {rangesByRow[3].length > 0
                ? rangeMarkers(rangesByRow[3], rangeLanes[3], (i) => rangeStrips[3].top + 16 + i * RANGE_STACK_STEP, toothCenters, colorOf, siglaOfFinding)
                : null}

              {lane("INFERIOR", height - LANE_H + 4, CHART_WIDTH, lowerLaneFindings, colorOf, siglaOfFinding, interactive && !rangeMode, selectArch)}

              {betweenFindings.map((f) => betweenMarker(f, toothCenters, colorOf, siglaOfFinding))}
            </svg>
          </div>
        </div>
        {/* Degradado del lado donde queda dibujo oculto (design D3). */}
        {overflow.left ? (
          <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 left-0 w-8" style={{ background: `linear-gradient(to right, ${PAPER}, transparent)` }} />
        ) : null}
        {overflow.right ? (
          <div aria-hidden="true" className="pointer-events-none absolute inset-y-0 right-0 w-8" style={{ background: `linear-gradient(to left, ${PAPER}, transparent)` }} />
        ) : null}
      </div>
      {overflow.left || overflow.right ? (
        <p className="mt-1 flex items-center justify-center gap-1.5 text-xs" style={{ color: INK_2 }}>
          <ArrowLeftRight aria-hidden="true" className="size-3.5" />
          Desliza para ver toda la boca
        </p>
      ) : null}

      <div aria-live="polite" className="sr-only">
        {rangeMode ? (rangeStart == null ? "Modo rango activo. Selecciona la primera pieza del rango." : `Primera pieza del rango: ${rangeStart}. Selecciona la última pieza, en la misma fila. Escape para cancelar.`) : ""}
      </div>

      <div className="mt-3 flex flex-wrap items-start gap-x-8 gap-y-2 text-xs" style={{ color: INK_2 }}>
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
  /** Superficie y entre-piezas se desactivan en modo rango (un solo gesto a la vez) — la pieza
   *  sigue activa (`interactive`) porque el click de pieza ES el gesto del rango. */
  surfaceInteractive: boolean,
  selectTooth: (fdi: number) => void,
  selectSurface: (fdi: number, surface: ToothSurfaceCode) => void,
  rangeStart: number | null,
  selectBetween: (toothA: number, toothB: number) => void,
) {
  const size = row.size;
  const crownY = row.upper ? SIGLA_H + ROW_GAP + NUM_H + ROW_GAP : 0;
  const numY = row.upper ? SIGLA_H + ROW_GAP : size + ROW_GAP;
  const sigY = row.upper ? 0 : size + ROW_GAP + NUM_H + ROW_GAP;
  const rowHeight = SIGLA_H + ROW_GAP + NUM_H + ROW_GAP + size;

  return (
    <g key={`row-${y}`}>
      {row.teeth.slice(0, -1).map((fdiA, i) => {
        const fdiB = row.teeth[i + 1];
        const xA = colX(colOffset + i) + size;
        const xB = colX(colOffset + i + 1);
        return (
          <g key={`gap-${fdiA}-${fdiB}`} transform={`translate(0, ${y})`}>
            <rect
              x={xA}
              y={0}
              width={xB - xA}
              height={rowHeight}
              fill="transparent"
              role="button"
              tabIndex={0}
              aria-label={`Entre las piezas ${fdiA} y ${fdiB}`}
              onClick={() => selectBetween(fdiA, fdiB)}
              onKeyDown={(e) => activateOnEnterOrSpace(e, () => selectBetween(fdiA, fdiB))}
              className={surfaceInteractive ? "cursor-pointer focus:outline-hidden" : "focus:outline-hidden"}
            />
            <circle cx={(xA + xB) / 2} cy={rowHeight / 2} r={2.5} fill={DIVIDER} pointerEvents="none" />
          </g>
        );
      })}
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
            {rangeStart === fdi ? (
              <rect x={-2} y={-2} width={size + 4} height={rowHeight + 4} rx={6} fill="none" stroke={SELECTION} strokeWidth={2} strokeDasharray="4 3" pointerEvents="none" />
            ) : null}
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
                y={sigY + SIGLA_H / 2 - (siglaEntries.length > 1 ? SIGLA_STACK_OFFSET - si * 2 * SIGLA_STACK_OFFSET : 0) + 5}
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
              className={interactive ? "cursor-pointer focus:outline-hidden" : "focus:outline-hidden"}
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
                    className={surfaceInteractive ? "cursor-pointer focus:outline-hidden" : "focus:outline-hidden"}
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
 * El carril de ARCO: solo el bar/botón de arco completo, cada tipo con su propio glifo (auditoría
 * completa 2026-09-25) — un aparato removible NO se ve igual que una prótesis completa, aunque
 * ambos sean de arco. Rango y entre-piezas quedan fuera de este carril (ver `rangeMarkers` y
 * `betweenMarker`) — compartir el carril de arco con rangos apilados terminaba pegando el tercero
 * a la corona o al propio bar de arco (bug real, probado en vivo, dos veces: primero en franja
 * temporal, después acá mismo con 3 rangos concurrentes en la fila permanente).
 */
function lane(
  arch: "SUPERIOR" | "INFERIOR",
  y: number,
  width: number,
  archFindings: DentalChartFindingResponse[],
  colorOf: (f: DentalChartFindingResponse) => string,
  siglaOfFinding: (f: DentalChartFindingResponse) => string,
  interactive: boolean,
  selectArch: (arch: "SUPERIOR" | "INFERIOR") => void,
) {
  const lastArch = archFindings[archFindings.length - 1];
  // La barra de SUPERIOR quedaba pegada (a veces solapada) con la primera fila de piezas — el
  // user lo notó probando en vivo. Más margen antes de la fila (INFERIOR ya tenía suficiente).
  const baseline = arch === "SUPERIOR" ? y + LANE_H - 14 : y + 6;

  return (
    <g
      role="button"
      tabIndex={0}
      aria-label={`Arco ${arch.toLowerCase()}${archFindings.length > 0 ? `: ${archFindings.map(siglaOfFinding).join(", ")}` : ", sin hallazgos de arco completo"}`}
      onClick={() => selectArch(arch)}
      onKeyDown={(e) => activateOnEnterOrSpace(e, () => selectArch(arch))}
      className={interactive ? "cursor-pointer focus:outline-hidden" : "focus:outline-hidden"}
    >
      {/* ⚠️ El objetivo de click real es TODA la franja (LANE_H), no la línea/glifo visual — de
          2-3px de grosor, prácticamente imposible de acertar con el mouse (bug real: el user no
          encontraba cómo registrar un hallazgo de arco, probando en vivo). */}
      <rect data-hit-area="true" x={0} y={y - 4} width={width} height={LANE_H} fill="transparent" />
      {lastArch ? (
        archGlyph(ARCH_GLYPH[lastArch.type ?? ""], 8, width - 8, baseline, colorOf(lastArch))
      ) : (
        <rect x={8} y={baseline - 1} width={width - 16} height={2} fill={DIVIDER} />
      )}
    </g>
  );
}

type RangeLanes = { laneOf: Map<number | undefined, number>; laneCount: number };

/**
 * Empaquetado de carriles por solapamiento horizontal real (mismo algoritmo que un calendario con
 * eventos concurrentes) — el carril NO es el índice en el array: dos rangos que no comparten ni
 * tocan piezas (ej. 17-14 y 13-11, en la misma fila pero sin solapar) van al MISMO carril, a la
 * misma altura — apilarlos sin necesidad confundía al user probando en vivo ("¿por qué quedaron a
 * distinta altura si no se tocan?"). Solo sube de carril cuando dos rangos SÍ se solapan en X.
 * Se calcula ANTES del layout (con `cxOf`, que no depende de la posición vertical de la fila) para
 * que la franja de cada fila mida solo lo que sus rangos realmente necesitan — no gastar 72px fijos
 * cuando la fila no tiene ningún rango, o tiene uno solo, era espacio en blanco sin uso real.
 */
function packRangeLanes(rangeFindings: DentalChartFindingResponse[], cxOf: (fdi: number) => number | undefined): RangeLanes {
  const GAP = 24;
  const items = rangeFindings.map((f) => {
    const teeth = f.location?.teeth ?? [];
    const x1raw = teeth[0] != null ? cxOf(teeth[0]) : undefined;
    const x2raw = teeth[teeth.length - 1] != null ? cxOf(teeth[teeth.length - 1]) : undefined;
    return { f, x1: Math.min(x1raw ?? 8, x2raw ?? 118), x2: Math.max(x1raw ?? 8, x2raw ?? 118) };
  });
  const laneEndX: number[] = [];
  const laneOf = new Map<number | undefined, number>();
  for (const it of [...items].sort((a, b) => a.x1 - b.x1)) {
    let lane = laneEndX.findIndex((end) => end + GAP <= it.x1);
    if (lane === -1) {
      lane = laneEndX.length;
      laneEndX.push(it.x2);
    } else {
      laneEndX[lane] = it.x2;
    }
    laneOf.set(it.f.id, lane);
  }
  return { laneOf, laneCount: laneEndX.length };
}

/** Sin rangos, casi nada (un separador visual mínimo); con 1+, lo justo para ese tantos carriles
 *  apilados — nunca un tamaño fijo que sobre o falte según el caso real (ver `packRangeLanes`). */
function rangeStripHeight(laneCount: number): number {
  // 28 = marcador a 16 del borde + sigla encima (trackY − 4, ~8 de alto): lo justo, sin aire extra.
  return laneCount === 0 ? 1 : 28 + (laneCount - 1) * RANGE_STACK_STEP;
}

/**
 * Marcadores de rango, posicionados con el centro real de sus piezas pilares (primera y última
 * del array) — nunca en un carril genérico. `trackYFor(lane)` decide la altura de un carril de
 * apilado: crece hacia la fila que describe o hacia afuera de ella según el llamador, porque un
 * carril externo (junto a fila permanente) y una franja interna (junto a fila temporal) apilan en
 * sentidos opuestos. `lanes` ya viene calculado (`packRangeLanes`, compartido con el layout que
 * decide el alto de la franja) — no se recalcula acá para no divergir del tamaño reservado.
 */
function rangeMarkers(
  rangeFindings: DentalChartFindingResponse[],
  lanes: RangeLanes,
  trackYFor: (lane: number) => number,
  toothCenters: Map<number, { cx: number; cy: number }>,
  colorOf: (f: DentalChartFindingResponse) => string,
  siglaOfFinding: (f: DentalChartFindingResponse) => string,
): ReactNode {
  return (
    <>
      {rangeFindings.map((f) => {
        const teeth = f.location?.teeth ?? [];
        const first = teeth[0] != null ? toothCenters.get(teeth[0]) : undefined;
        const last = teeth[teeth.length - 1] != null ? toothCenters.get(teeth[teeth.length - 1]) : undefined;
        const x1 = Math.min(first?.cx ?? 8, last?.cx ?? 118);
        const x2 = Math.max(first?.cx ?? 8, last?.cx ?? 118);
        const label = `Rango de piezas ${teeth.join(", ")}: ${siglaOfFinding(f)}`;
        const trackY = trackYFor(lanes.laneOf.get(f.id) ?? 0);
        return (
          <g key={f.id} role="img" aria-label={label}>
            {rangeGlyph(RANGE_GLYPH[f.type ?? ""], x1, x2, trackY, colorOf(f))}
            <text x={x1} y={trackY - 4} fontFamily={FONT_MONO} fontSize={10} fontWeight={700} fill={colorOf(f)}>
              {siglaOfFinding(f)}
            </text>
          </g>
        );
      })}
    </>
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
