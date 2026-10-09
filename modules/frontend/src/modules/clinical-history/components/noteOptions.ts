import type { SelectOption } from "@/modules/core/ui/form-field";
import {
  ActiveBackgroundItemSource,
  BackgroundEntryRequestCategory,
  CorrectionRequestField,
} from "@/modules/core/services/generated/model";
import type { NoteChecklistField } from "@/modules/clinical-history/schemas/consultationNote";

/**
 * Campos de texto de la nota, en el orden de NTS 139 §5.2.1 (1) (design.md D3). La clave es el
 * nombre del campo en la API; también es el `field` de una corrección.
 */
export const NOTE_TEXT_FIELDS = [
  { field: CorrectionRequestField.REASON, key: "reason", label: "Motivo de consulta" },
  { field: CorrectionRequestField.ILLNESS_DURATION, key: "illnessDuration", label: "Tiempo de enfermedad" },
  { field: CorrectionRequestField.SYMPTOMS_AND_SIGNS, key: "symptomsAndSigns", label: "Síntomas y signos" },
  {
    field: CorrectionRequestField.PREVIOUS_TREATMENT,
    key: "previousTreatment",
    label: "Tratamiento recibido, cumplimiento y resultado",
  },
  { field: CorrectionRequestField.BIOLOGICAL_FUNCTIONS, key: "biologicalFunctions", label: "Funciones biológicas" },
  { field: CorrectionRequestField.EXAMINATION, key: "examination", label: "Examen (evaluación odontológica)" },
  { field: CorrectionRequestField.TREATMENT, key: "treatment", label: "Tratamiento" },
  { field: CorrectionRequestField.AUXILIARY_EXAMS, key: "auxiliaryExams", label: "Exámenes auxiliares" },
  { field: CorrectionRequestField.REFERRAL, key: "referral", label: "Referencia" },
] as const;

export type NoteTextKey = (typeof NOTE_TEXT_FIELDS)[number]["key"];

/** Lo que exige el sellado, con los nombres que devuelve el servidor en `missingFields`. */
export const NOTE_CHECKLIST_LABELS: Record<NoteChecklistField, string> = {
  reason: "Motivo de consulta",
  examination: "Examen (evaluación odontológica)",
  treatment: "Tratamiento",
  diagnoses: "Al menos un diagnóstico CIE10",
};

export const backgroundCategoryOptions: SelectOption[] = [
  { value: BackgroundEntryRequestCategory.ALERGIA_RAM, label: "Alergia o reacción adversa" },
  { value: BackgroundEntryRequestCategory.ANTECEDENTE_PATOLOGICO, label: "Antecedente patológico" },
  { value: BackgroundEntryRequestCategory.MEDICACION, label: "Medicación actual" },
];

/** De dónde viene un antecedente vigente (design.md D8). */
export const BACKGROUND_SOURCE_LABELS: Record<string, string> = {
  [ActiveBackgroundItemSource.HISTORIA]: "Historia Clínica",
  [ActiveBackgroundItemSource.NOTA]: "Consulta",
  [ActiveBackgroundItemSource.FICHA]: "Ficha del paciente, sin autoría clínica",
};
