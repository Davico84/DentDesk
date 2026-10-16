/** Un paso de un recorrido guiado. Primitivo genérico: no conoce ningún dominio. */
export interface TourStep {
  /** Elemento que el paso resalta. Sin ancla (o si no existe en el DOM), la tarjeta va centrada. */
  anchor?: () => Element | null;
  title: string;
  /** Texto plano: también es lo que se anuncia al lector de pantalla. */
  body: string;
  /** Ejemplos opcionales que se listan bajo el texto (p. ej., hallazgos del catálogo). */
  examples?: string[];
}

export type TourCloseReason = "done" | "closed";

/** Rectángulo del ancla en coordenadas de la ventana, ya con el margen del recorte. */
export interface SpotRect {
  top: number;
  left: number;
  width: number;
  height: number;
}
