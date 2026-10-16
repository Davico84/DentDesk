import type { FindingScope } from "./chartGuideSteps";

/**
 * Modo ubicar (update-dental-chart-guidance design D5): el odontograma espera que el usuario toque una zona de
 * `scope`, y solo responden esas. Generaliza el modo rango: "Registrar rango" es `{ scope: "TOOTH_RANGE" }` sin tipo.
 * Vive en `DentalChartEditor` (un solo estado, también el ancla del rango); `Odontogram` lo recibe controlado.
 */
export type Placement = {
  scope: FindingScope;
  /** Tipo ya elegido: el registro de la zona que se toque arranca con él. */
  type?: string;
  /** Primera pieza del rango, cuando `scope` es TOOTH_RANGE. */
  rangeStart?: number;
  /** Pieza desde la que se pidió ubicar, para nombrarla en el aviso ("una cara de la pieza 36"). */
  fromTooth?: number;
};
