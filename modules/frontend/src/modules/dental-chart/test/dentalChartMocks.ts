import { http, HttpResponse, delay } from "msw";
import type { DentalChartResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";

/**
 * Mocks de la API del odontograma para los tests de la guía y del diálogo. MSW corre con `onUnhandledRequest:
 * "error"` y no hay handlers globales: cada test los instala con `server.use(...dentalChartHandlers(...))`.
 */
const entry = (type: string, scope: DentalFindingCatalogEntryResponse["scope"], subtype = ""): DentalFindingCatalogEntryResponse =>
  ({ type, subtype, scope, acronym: "X", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false }) as DentalFindingCatalogEntryResponse;

/** Un tipo por cada uso que prueban los tests, con su alcance real del catálogo (V16). */
export const CATALOG: DentalFindingCatalogEntryResponse[] = [
  entry("LESION_CARIES", "SURFACE", "MB"),
  entry("RESTAURACION_DEFINITIVA", "SURFACE"),
  entry("SELLANTE", "SURFACE"),
  entry("CORONA", "TOOTH"),
  entry("TRATAMIENTO_CONDUCTO", "TOOTH"),
  entry("PIEZA_AUSENTE", "TOOTH"),
  entry("DIASTEMA", "BETWEEN_TEETH"),
  entry("PROTESIS_COMPLETA", "ARCH"),
  entry("EDENTULO_TOTAL", "ARCH"),
  entry("PROTESIS_PARCIAL_FIJA", "TOOTH_RANGE"),
];

export function chartBlock(status: "OPEN" | "SEALED", findings: DentalChartResponse["findings"] = []): DentalChartResponse {
  return {
    id: 1, patientId: 1, type: "INITIAL", status, examinedOn: "2026-10-01", findings,
    specifications: "", observations: "", specificationsReviewed: false, observationsReviewed: false,
  } as unknown as DentalChartResponse;
}

export function dentalChartHandlers({
  block = chartBlock("OPEN"),
  blockDelayMs = 0,
}: { block?: DentalChartResponse; blockDelayMs?: number } = {}) {
  return [
    http.get("*/patients/:id/dental-chart", async () => {
      if (blockDelayMs) await delay(blockDelayMs);
      return HttpResponse.json(block);
    }),
    http.get("*/dental-chart/catalog", () => HttpResponse.json(CATALOG)),
  ];
}
