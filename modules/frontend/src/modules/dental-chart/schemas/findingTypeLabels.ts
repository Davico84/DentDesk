import { DentalChartFindingRequestType } from "@/modules/core/services/generated/model";

/**
 * Etiqueta legible de cada uno de los 38 tipos del catálogo (NTS 188 6.1.1-6.1.38). El backend
 * es la fuente de los **atributos** del catálogo (design.md D6); esto solo traduce el nombre del
 * enum a un rótulo de UI, no reinventa alcance ni comportamiento de color.
 */
export const FINDING_TYPE_LABELS: Record<string, string> = {
  APARATO_ORTODONTICO_FIJO: "Aparato ortodóntico fijo",
  APARATO_ORTODONTICO_REMOVIBLE: "Aparato ortodóntico removible",
  CORONA: "Corona",
  CORONA_TEMPORAL: "Corona temporal",
  DEFECTOS_DESARROLLO_ESMALTE: "Defectos del desarrollo del esmalte",
  DIASTEMA: "Diastema",
  EDENTULO_TOTAL: "Edéntulo total",
  ESPIGO_MUNON: "Espigo muñón",
  FOSAS_FISURAS_PROFUNDAS: "Fosas y fisuras profundas",
  FRACTURA_DENTAL: "Fractura dental",
  FUSION: "Fusión",
  GEMINACION: "Geminación",
  GIROVERSION: "Giroversión",
  IMPACTACION: "Impactación",
  IMPLANTE_DENTAL: "Implante dental",
  LESION_CARIES: "Lesión de caries",
  MACRODONCIA: "Macrodoncia",
  MICRODONCIA: "Microdoncia",
  MOVILIDAD_PATOLOGICA: "Movilidad patológica",
  PIEZA_AUSENTE: "Pieza ausente",
  PIEZA_EN_CLAVIJA: "Pieza en clavija",
  PIEZA_ECTOPICA: "Pieza ectópica",
  PIEZA_EN_ERUPCION: "Pieza en erupción",
  PIEZA_EXTRUIDA: "Pieza extruida",
  PIEZA_INTRUIDA: "Pieza intruida",
  PIEZA_SUPERNUMERARIA: "Pieza supernumeraria",
  PULPOTOMIA: "Pulpotomía",
  POSICION_ANORMAL: "Posición anormal",
  PROTESIS_PARCIAL_FIJA: "Prótesis parcial fija",
  PROTESIS_COMPLETA: "Prótesis completa",
  PROTESIS_PARCIAL_REMOVIBLE: "Prótesis parcial removible",
  REMANENTE_RADICULAR: "Remanente radicular",
  RESTAURACION_DEFINITIVA: "Restauración definitiva",
  RESTAURACION_TEMPORAL: "Restauración temporal",
  SELLANTE: "Sellante",
  SUPERFICIE_DESGASTADA: "Superficie desgastada",
  TRATAMIENTO_CONDUCTO: "Tratamiento de conducto",
  TRANSPOSICION_DENTARIA: "Transposición dentaria",
};

export function findingTypeLabel(type: string): string {
  return FINDING_TYPE_LABELS[type] ?? type;
}

/** Verifica en desarrollo que todo valor del enum generado tenga etiqueta (evita un `undefined` en producción). */
export function assertAllFindingTypesLabeled(): void {
  const missing = Object.values(DentalChartFindingRequestType).filter((t) => !(t in FINDING_TYPE_LABELS));
  if (missing.length > 0) {
    throw new Error(`Faltan etiquetas para: ${missing.join(", ")}`);
  }
}
