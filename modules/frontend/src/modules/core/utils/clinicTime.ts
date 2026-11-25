/**
 * Hora civil de la clínica ↔ instante (add-appointments design.md D3). Primitivo genérico de `core`, junto a
 * `clinicDate.ts`.
 *
 * La API habla en instantes (UTC); la recepción ve y elige horas **de la clínica**, nunca del navegador: una
 * laptop configurada en otra zona vería las citas corridas. La zona llega del servidor (`GET /business-hours`).
 * Solo `Intl`, sin librería de fechas.
 */

/** Fecha y hora civiles en una zona: `date` = `YYYY-MM-DD`, `time` = `HH:mm`, `offset` = `±HH:mm`. */
export interface ClinicLocal {
  date: string;
  time: string;
  offset: string;
}

/** Resultado de convertir una hora civil a instante. `adjusted` = la hora no existía ese día y se avanzó. */
export interface ResolvedInstant {
  instant: Date;
  /** La hora civil que de verdad queda, con su desfase (puede diferir de la pedida si se ajustó). */
  local: ClinicLocal;
  adjusted: boolean;
}

const MINUTE = 60_000;
const formatters = new Map<string, Intl.DateTimeFormat>();

function formatterFor(timeZone: string): Intl.DateTimeFormat {
  let formatter = formatters.get(timeZone);
  if (!formatter) {
    formatter = new Intl.DateTimeFormat("en-CA", {
      timeZone,
      hourCycle: "h23",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    });
    formatters.set(timeZone, formatter);
  }
  return formatter;
}

/** Partes civiles de un instante en la zona, como milisegundos "si fuera UTC" (para restar y obtener el desfase). */
function wallClockMillis(instantMs: number, timeZone: string): number {
  const parts = Object.fromEntries(
    formatterFor(timeZone)
      .formatToParts(new Date(instantMs))
      .filter((p) => p.type !== "literal")
      .map((p) => [p.type, Number(p.value)]),
  ) as Record<string, number>;
  return Date.UTC(parts.year, parts.month - 1, parts.day, parts.hour, parts.minute, parts.second);
}

/** Desfase de la zona en ese instante, en minutos (Lima: -300). */
function offsetMinutes(instantMs: number, timeZone: string): number {
  return Math.round((wallClockMillis(instantMs, timeZone) - Math.floor(instantMs / 1000) * 1000) / MINUTE);
}

function pad(n: number): string {
  return String(n).padStart(2, "0");
}

function formatOffset(minutes: number): string {
  const sign = minutes < 0 ? "-" : "+";
  const abs = Math.abs(minutes);
  return `${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`;
}

/** Fecha, hora y desfase civiles de un instante en la zona de la clínica. */
export function instantToClinicLocal(instant: Date | string, timeZone: string): ClinicLocal {
  const ms = typeof instant === "string" ? Date.parse(instant) : instant.getTime();
  const wall = new Date(wallClockMillis(ms, timeZone));
  return {
    date: `${wall.getUTCFullYear()}-${pad(wall.getUTCMonth() + 1)}-${pad(wall.getUTCDate())}`,
    time: `${pad(wall.getUTCHours())}:${pad(wall.getUTCMinutes())}`,
    offset: formatOffset(offsetMinutes(ms, timeZone)),
  };
}

/**
 * Instante de una fecha y hora civiles de la clínica. Con cambio de horario (no aplica a Lima):
 * - hora **repetida** (retroceso): su **primera** ocurrencia, el instante anterior;
 * - hora **inexistente** (salto): se avanza al **primer instante válido**, el del cambio, y `adjusted` lo dice.
 * El diálogo muestra `local` con su desfase antes de confirmar.
 */
export function clinicLocalToInstant(date: string, time: string, timeZone: string): ResolvedInstant {
  const [y, mo, d] = date.split("-").map(Number);
  const [h, mi] = time.split(":").map(Number);
  const asUtc = Date.UTC(y, mo - 1, d, h, mi);
  // Los desfases posibles ese día: el de antes y el de después de un eventual cambio.
  const offsets = [...new Set([offsetMinutes(asUtc - 12 * 60 * MINUTE, timeZone), offsetMinutes(asUtc + 12 * 60 * MINUTE, timeZone)])];
  const valid = offsets
    .map((offset) => asUtc - offset * MINUTE)
    .filter((candidate) => wallClockMillis(candidate, timeZone) === asUtc)
    .sort((a, b) => a - b);

  if (valid.length > 0) {
    const instant = new Date(valid[0]);
    return { instant, local: instantToClinicLocal(instant, timeZone), adjusted: false };
  }

  // Hueco: entre los dos candidatos está el cambio; se busca el primer minuto cuya hora civil ya es posterior.
  const candidates = offsets.map((offset) => asUtc - offset * MINUTE).sort((a, b) => a - b);
  let low = candidates[0];
  let high = candidates[candidates.length - 1];
  while (high - low > MINUTE) {
    const mid = low + Math.floor((high - low) / 2 / MINUTE) * MINUTE;
    if (wallClockMillis(mid, timeZone) > asUtc) {
      high = mid;
    } else {
      low = mid;
    }
  }
  const instant = new Date(high);
  return { instant, local: instantToClinicLocal(instant, timeZone), adjusted: true };
}

/** Día de la semana ISO (1 = lunes … 7 = domingo) de una fecha `YYYY-MM-DD`. */
export function isoWeekday(date: string): number {
  const day = new Date(`${date}T00:00:00Z`).getUTCDay();
  return day === 0 ? 7 : day;
}

/** Suma días a una fecha `YYYY-MM-DD`, sin zona (aritmética de calendario). */
export function addDays(date: string, days: number): string {
  const ms = Date.parse(`${date}T00:00:00Z`) + days * 24 * 60 * MINUTE;
  return new Date(ms).toISOString().slice(0, 10);
}

/** Lunes de la semana de una fecha `YYYY-MM-DD`. */
export function mondayOf(date: string): string {
  return addDays(date, 1 - isoWeekday(date));
}
