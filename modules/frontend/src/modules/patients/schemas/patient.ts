import { z } from "zod";
import {
  PatientRequestDocumentType,
  PatientRequestGuardianRelationship,
  PatientRequestSex,
} from "@/modules/core/services/generated/model";
import type { PatientRequest, PatientResponse } from "@/modules/core/services/generated/model";

/** Mayoría de edad, en paridad con el backend (`common/Ages`, design.md D2). */
const ADULT_AGE = 18;

/** Minoría derivada de la fecha de nacimiento (`YYYY-MM-DD`). Cadena vacía o inválida → false. */
export function isMinor(birthDate: string): boolean {
  if (!birthDate) return false;
  const born = new Date(birthDate);
  if (Number.isNaN(born.getTime())) return false;
  const now = new Date();
  let age = now.getFullYear() - born.getFullYear();
  const m = now.getMonth() - born.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < born.getDate())) age--;
  return age < ADULT_AGE;
}

/**
 * Formato del documento **según su tipo**, en paridad con el enum `DocumentType` del backend
 * (design.md D8). El documento es texto, nunca numérico: el pasaporte es alfanumérico y un número
 * tiraría los ceros a la izquierda.
 */
const DOCUMENT_PATTERNS: Record<string, RegExp> = {
  [PatientRequestDocumentType.DNI]: /^\d{8}$/,
  [PatientRequestDocumentType.CE]: /^\d{9,12}$/,
  [PatientRequestDocumentType.PASSPORT]: /^[A-Za-z0-9]{6,12}$/,
};

/** Ayuda de formato que se muestra bajo el número, según el tipo elegido. */
export function documentHint(documentType: string): string | undefined {
  switch (documentType) {
    case PatientRequestDocumentType.DNI:
      return "8 dígitos.";
    case PatientRequestDocumentType.CE:
      return "9 a 12 dígitos.";
    case PatientRequestDocumentType.PASSPORT:
      return "6 a 12 caracteres alfanuméricos.";
    default:
      return undefined;
  }
}

/**
 * Largo máximo del documento según su tipo. **Impedir es mejor que corregir**: sin esto puedes
 * teclear 20 dígitos en un DNI y solo enterarte al fallar. El input no te deja pasar del límite.
 */
export function documentMaxLength(documentType: string): number | undefined {
  switch (documentType) {
    case PatientRequestDocumentType.DNI:
      return 8;
    case PatientRequestDocumentType.CE:
    case PatientRequestDocumentType.PASSPORT:
      return 12;
    default:
      return undefined;
  }
}

/** Código de país usado por defecto en la operación de la clínica. */
export const PHONE_COUNTRY_CODE = "+51";

/** Formato internacional E.164, sin separadores: + seguido de 7 a 15 dígitos. */
const PHONE = /^\+[1-9]\d{6,14}$/;
const PHONE_MESSAGE = "Ingresa un número internacional válido, sin espacios.";

/**
 * Ayuda **persistente** del teléfono. Va bajo el campo, no en un placeholder: el placeholder
 * desaparece justo al escribir, que es cuando hace falta. Sin esto, la regla solo se descubre
 * fallando.
 */
export const PHONE_HINT = "Elige el país y escribe el número sin espacios.";

/** Campo opcional de conjunto cerrado: el valor del enum, o "" cuando no se ha elegido. */
const optionalEnum = <T extends Record<string, string>>(values: T) =>
  z.union([z.enum(values), z.literal("")]);

// `+51` es el valor inicial visible; por sí solo no cuenta como un teléfono registrado.
const optionalPhone = z.union([z.string().regex(PHONE, PHONE_MESSAGE), z.literal(""), z.literal(PHONE_COUNTRY_CODE)]);

/**
 * Formulario de la ficha del paciente. Solo `fullName` y `birthDate` son obligatorios (design.md
 * OQ1). En **paridad con el servidor** (`docs/coding-style.md §7`): el responsable es obligatorio
 * si la fecha indica menor (D2), y el documento va **emparejado con su tipo** con **formato según
 * el tipo** (D8). El servidor sigue siendo la autoridad: si el cliente se despista, su `400` trae
 * el detalle por campo y se pinta junto al input.
 */
export const patientSchema = z
  .object({
    fullName: z.string().trim().min(1, "El nombre es obligatorio").max(200),
    documentType: optionalEnum(PatientRequestDocumentType),
    document: z.string().trim().max(50),
    // Paridad con el @PastOrPresent del backend. Sin esto se podía escribir que el paciente nace
    // en 2050: el cliente lo aceptaba y el servidor devolvía un 400. Y la edad DERIVA de esta
    // fecha (menor→responsable, dentición), así que una fecha futura envenena el resto.
    birthDate: z
      .string()
      .min(1, "La fecha de nacimiento es obligatoria")
      .refine((v) => !Number.isNaN(new Date(v).getTime()), "Fecha inválida")
      .refine((v) => new Date(v) <= new Date(), "La fecha no puede estar en el futuro"),
    sex: optionalEnum(PatientRequestSex),
    phone: optionalPhone,
    email: z.union([z.email("Correo inválido"), z.literal("")]),
    address: z.string().trim().max(500),
    guardianName: z.string().trim().max(200),
    guardianRelationship: optionalEnum(PatientRequestGuardianRelationship),
    guardianDocument: z.string().trim().max(50),
    guardianPhone: optionalPhone,
    emergencyContactName: z.string().trim().max(200),
    emergencyContactPhone: optionalPhone,
    emergencyContactRelationship: z.string().trim().max(50),
    // Anamnesis: en el formulario se edita como texto separado por comas; se convierte a lista.
    allergies: z.string(),
    conditions: z.string(),
    currentMedication: z.string(),
  })
  .superRefine((values, ctx) => {
    // --- Responsable obligatorio si es menor (design.md D2) ---
    if (isMinor(values.birthDate)) {
      if (!values.guardianName.trim()) {
        ctx.addIssue({ code: "custom", path: ["guardianName"], message: MINOR_REQUIRED });
      }
      if (!values.guardianRelationship) {
        ctx.addIssue({ code: "custom", path: ["guardianRelationship"], message: MINOR_REQUIRED });
      }
      if (!values.guardianPhone.trim() || values.guardianPhone === PHONE_COUNTRY_CODE) {
        ctx.addIssue({ code: "custom", path: ["guardianPhone"], message: MINOR_REQUIRED });
      }
    }

    // --- Contacto de emergencia obligatorio si no hay teléfono propio (update-patients D2) ---
    const hasOwnPhone = values.phone.trim() !== "" && values.phone !== PHONE_COUNTRY_CODE;
    if (!hasOwnPhone) {
      if (!values.emergencyContactName.trim()) {
        ctx.addIssue({ code: "custom", path: ["emergencyContactName"], message: EMERGENCY_CONTACT_REQUIRED });
      }
      if (!values.emergencyContactPhone.trim() || values.emergencyContactPhone === PHONE_COUNTRY_CODE) {
        ctx.addIssue({ code: "custom", path: ["emergencyContactPhone"], message: EMERGENCY_CONTACT_REQUIRED });
      }
      if (!values.emergencyContactRelationship.trim()) {
        ctx.addIssue({
          code: "custom",
          path: ["emergencyContactRelationship"],
          message: EMERGENCY_CONTACT_REQUIRED,
        });
      }
    }

    // --- Documento: emparejado con su tipo, y con formato según el tipo (design.md D8) ---
    const hasNumber = values.document.trim().length > 0;
    const hasType = values.documentType !== "";
    if (!hasType && !hasNumber) return; // el documento es opcional

    if (!hasType) {
      ctx.addIssue({ code: "custom", path: ["documentType"], message: "Indica el tipo de documento." });
      return;
    }
    if (!hasNumber) {
      ctx.addIssue({ code: "custom", path: ["document"], message: "Indica el número de documento." });
      return;
    }
    const pattern = DOCUMENT_PATTERNS[values.documentType];
    if (pattern && !pattern.test(values.document.trim())) {
      ctx.addIssue({
        code: "custom",
        path: ["document"],
        message: `Formato inválido. ${documentHint(values.documentType) ?? ""}`.trim(),
      });
    }
  });

const MINOR_REQUIRED = "Obligatorio para un paciente menor de edad.";
const EMERGENCY_CONTACT_REQUIRED = "Obligatorio si el paciente no tiene teléfono propio.";

export type PatientValues = z.infer<typeof patientSchema>;

/** Lista separada por comas → array de valores limpios. */
function toList(text: string): string[] {
  return text
    .split(",")
    .map((v) => v.trim())
    .filter((v) => v.length > 0);
}

function joinList(values: string[] | undefined): string {
  return (values ?? []).join(", ");
}

/** Convierte el formulario al request del contrato. Los vacíos se omiten (`undefined`). */
export function toRequest(values: PatientValues): PatientRequest {
  const clean = (v: string) => (v.trim() === "" ? undefined : v.trim());
  const phone = (v: string) => {
    const number = v.replace(/\s/g, "");
    return number && number !== PHONE_COUNTRY_CODE ? number : undefined;
  };
  const pick = <T extends string>(v: T | "") => (v === "" ? undefined : v);
  return {
    fullName: values.fullName.trim(),
    documentType: pick(values.documentType),
    document: clean(values.document),
    birthDate: values.birthDate,
    sex: pick(values.sex),
    phone: phone(values.phone),
    email: clean(values.email),
    address: clean(values.address),
    guardianName: clean(values.guardianName),
    guardianRelationship: pick(values.guardianRelationship),
    guardianDocument: clean(values.guardianDocument),
    guardianPhone: phone(values.guardianPhone),
    emergencyContactName: clean(values.emergencyContactName),
    emergencyContactPhone: phone(values.emergencyContactPhone),
    emergencyContactRelationship: clean(values.emergencyContactRelationship),
    allergies: toList(values.allergies),
    conditions: toList(values.conditions),
    currentMedication: toList(values.currentMedication),
  };
}

/** Valores por defecto del formulario a partir de una ficha existente (edición). */
export function toDefaults(patient?: PatientResponse): PatientValues {
  // Los registros existentes pueden contener separadores; se muestran normalizados en E.164.
  const phoneDefault = (phone: string | undefined) => {
    const digits = (phone ?? "").replace(/\D/g, "");
    return digits ? `+${digits}` : PHONE_COUNTRY_CODE;
  };
  return {
    fullName: patient?.fullName ?? "",
    documentType: patient?.documentType ?? "",
    document: patient?.document ?? "",
    birthDate: patient?.birthDate ?? "",
    sex: patient?.sex ?? "",
    phone: phoneDefault(patient?.phone),
    email: patient?.email ?? "",
    address: patient?.address ?? "",
    guardianName: patient?.guardianName ?? "",
    guardianRelationship: patient?.guardianRelationship ?? "",
    guardianDocument: patient?.guardianDocument ?? "",
    guardianPhone: phoneDefault(patient?.guardianPhone),
    emergencyContactName: patient?.emergencyContactName ?? "",
    emergencyContactPhone: phoneDefault(patient?.emergencyContactPhone),
    emergencyContactRelationship: patient?.emergencyContactRelationship ?? "",
    allergies: joinList(patient?.allergies),
    conditions: joinList(patient?.conditions),
    currentMedication: joinList(patient?.currentMedication),
  };
}
