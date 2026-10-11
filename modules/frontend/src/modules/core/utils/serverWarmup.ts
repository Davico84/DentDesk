/**
 * Estados de la espera al cargar la app (`add-cold-start-warmup` D5). Puros y con nombre, para que
 * la pantalla y sus tests compartan los umbrales.
 */
export type WarmupState = "loading" | "warming" | "stuck" | "offline";

/** Hasta aquí la espera es normal: solo "Cargando…". */
export const WARMING_AFTER_S = 4;
/** Desde aquí la ayuda dice "Ya casi está…". */
export const ALMOST_AFTER_S = 45;
/** Desde aquí se ofrece reintentar. */
export const STUCK_AFTER_S = 90;

/** Sin red manda desde el segundo 0: no se confunde una red caída con el servidor dormido. */
export function warmupState(elapsedSeconds: number, online: boolean): WarmupState {
  if (!online) return "offline";
  if (elapsedSeconds >= STUCK_AFTER_S) return "stuck";
  if (elapsedSeconds >= WARMING_AFTER_S) return "warming";
  return "loading";
}

/**
 * Progreso ESTIMADO (no medido): avanza rápido al inicio, ronda el 85 % al minuto y nunca llega solo
 * al 100 %. Sin pasos inventados que no se pueden medir.
 */
export function estimatedProgress(elapsedSeconds: number): number {
  return 92 * (1 - Math.exp(-elapsedSeconds / 22));
}
