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

/**
 * Nombre completo de cada subtipo, verificado entrada por entrada contra el PDF oficial de NTS
 * 188 (páginas 8, 11, 13, 15, 17 y 18 de `nts-188-odontograma-rm-559-2022-ESCANEADA.pdf`) — no es
 * un análisis derivado. Clave `type:subtype`, como en el resto del catálogo (design.md D6): el
 * código de un subtipo no es único por sí solo entre tipos distintos, aunque hoy no colisione.
 * Solo cubre los 7 tipos que tienen subtipos — el resto del catálogo no los necesita.
 */
const SUBTYPE_FULL_NAMES: Record<string, string> = {
  // 6.1.3 CORONA
  "CORONA:CM": "Corona Metálica",
  "CORONA:CF": "Corona Fenestrada",
  "CORONA:CMC": "Corona Metal Cerámica",
  "CORONA:CV": "Corona Veneer",
  "CORONA:CLM": "Corona Libre de Metal",
  // 6.1.5 DEFECTOS DEL DESARROLLO DEL ESMALTE
  "DEFECTOS_DESARROLLO_ESMALTE:O": "Opacidades del esmalte",
  "DEFECTOS_DESARROLLO_ESMALTE:PE": "Pigmentación del esmalte",
  // 6.1.16 LESIÓN DE CARIES DENTAL
  "LESION_CARIES:MB": "Mancha Blanca",
  "LESION_CARIES:CE": "Lesión de caries a nivel del esmalte",
  "LESION_CARIES:CD": "Lesión de caries a nivel de la dentina",
  "LESION_CARIES:CDP": "Lesión de caries a nivel de la dentina, con compromiso de la pulpa",
  // 6.1.20 PIEZA DENTARIA AUSENTE
  "PIEZA_AUSENTE:DNE": "Diente no erupcionado",
  "PIEZA_AUSENTE:DEX": "Diente ausente por extracción (caries)",
  "PIEZA_AUSENTE:DAO": "Diente ausente por otra razón",
  // 6.1.28 POSICIÓN ANORMAL DENTARIA
  "POSICION_ANORMAL:M": "Mesializado",
  "POSICION_ANORMAL:D": "Distalizado",
  "POSICION_ANORMAL:V": "Vestibularizado",
  "POSICION_ANORMAL:P": "Palatinizado",
  "POSICION_ANORMAL:L": "Lingualizado",
  // 6.1.33 RESTAURACIÓN DEFINITIVA
  "RESTAURACION_DEFINITIVA:AM": "Amalgama Dental",
  "RESTAURACION_DEFINITIVA:R": "Resina",
  "RESTAURACION_DEFINITIVA:IV": "Ionómero de Vidrio",
  "RESTAURACION_DEFINITIVA:IM": "Incrustación Metálica",
  "RESTAURACION_DEFINITIVA:IE": "Incrustación Estética",
  "RESTAURACION_DEFINITIVA:C": "Carilla",
  // 6.1.37 TRATAMIENTO DE CONDUCTO
  "TRATAMIENTO_CONDUCTO:TC": "Tratamiento de conductos",
  "TRATAMIENTO_CONDUCTO:PC": "Pulpectomía",
};

/** "CM (Corona Metálica)" — para que el odontólogo no tenga que memorizar la sigla. Si el par no
 *  está en el mapa (no debería pasar, cubre los 7 tipos con subtipos), cae a la sigla sola. */
export function subtypeOptionLabel(type: string, subtypeCode: string, acronym: string): string {
  const fullName = SUBTYPE_FULL_NAMES[`${type}:${subtypeCode}`];
  return fullName ? `${acronym} (${fullName})` : acronym;
}

/** Verifica en desarrollo que todo valor del enum generado tenga etiqueta (evita un `undefined` en producción). */
export function assertAllFindingTypesLabeled(): void {
  const missing = Object.values(DentalChartFindingRequestType).filter((t) => !(t in FINDING_TYPE_LABELS));
  if (missing.length > 0) {
    throw new Error(`Faltan etiquetas para: ${missing.join(", ")}`);
  }
}
