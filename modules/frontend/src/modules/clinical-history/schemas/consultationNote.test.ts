import { describe, it, expect } from "vitest";
import {
  backgroundAltaSchema,
  clinicDay,
  consultationNoteSchema,
  correctionSchema,
  missingNoteFields,
  noteDefaults,
  toLocalDateTimeInput,
  toNoteRequest,
} from "./consultationNote";

const base = noteDefaults(undefined);

function localInput(offsetMs: number): string {
  return toLocalDateTimeInput(new Date(Date.now() + offsetMs).toISOString());
}

/** Paridad con `ConsultationNoteRequest` y las reglas del servicio (design.md D3/D4). */
describe("consultationNoteSchema", () => {
  it("acepta un borrador con solo la fecha de atención", () => {
    expect(consultationNoteSchema.safeParse({ ...base, attendedAt: localInput(-3_600_000) }).success).toBe(true);
  });

  it("exige la fecha y hora de la atención", () => {
    const result = consultationNoteSchema.safeParse(base);
    expect(result.success).toBe(false);
    expect(result.error?.issues[0]?.path).toEqual(["attendedAt"]);
  });

  it("rechaza una atención futura", () => {
    const result = consultationNoteSchema.safeParse({ ...base, attendedAt: localInput(3_600_000 * 3) });
    expect(result.error?.issues.map((i) => i.message)).toContain("La atención no puede ser futura.");
  });

  it("la próxima cita debe ser posterior al día de la atención", () => {
    const attendedAt = "2026-10-01T10:00";
    const sameDay = consultationNoteSchema.safeParse({ ...base, attendedAt, nextAppointmentOn: "2026-10-01" });
    const before = consultationNoteSchema.safeParse({ ...base, attendedAt, nextAppointmentOn: "2026-09-30" });
    const after = consultationNoteSchema.safeParse({ ...base, attendedAt, nextAppointmentOn: "2026-10-08" });
    expect(sameDay.success).toBe(false);
    expect(before.success).toBe(false);
    expect(after.success).toBe(true);
  });

  it("compara la próxima cita con el día de la clínica, no con el del navegador", () => {
    // 03:00 UTC del 10 de septiembre es todavía el 9 en Lima: el día de la atención para la clínica es el 9.
    const attendedAt = toLocalDateTimeInput("2026-09-10T03:00:00Z");
    expect(clinicDay(new Date("2026-09-10T03:00:00Z"))).toBe("2026-09-09");
    expect(consultationNoteSchema.safeParse({ ...base, attendedAt, nextAppointmentOn: "2026-09-09" }).success).toBe(false);
    expect(consultationNoteSchema.safeParse({ ...base, attendedAt, nextAppointmentOn: "2026-09-10" }).success).toBe(true);
  });

  it("acepta una atención con el margen de un minuto del reloj y rechaza más allá", () => {
    expect(consultationNoteSchema.safeParse({ ...base, attendedAt: localInput(0) }).success).toBe(true);
    expect(consultationNoteSchema.safeParse({ ...base, attendedAt: localInput(5 * 60_000) }).success).toBe(false);
  });

  it("los campos vacíos viajan como undefined y la fecha como instante ISO", () => {
    const request = toNoteRequest({ ...base, attendedAt: "2026-10-01T10:00", reason: "Dolor" });
    expect(request.reason).toBe("Dolor");
    expect(request.examination).toBeUndefined();
    expect(request.attendedAt).toBe(new Date("2026-10-01T10:00").toISOString());
  });
});

describe("missingNoteFields — espejo del checklist de sellado", () => {
  it("lista motivo, examen, tratamiento y diagnóstico cuando faltan", () => {
    expect(missingNoteFields({ diagnoses: [] })).toEqual(["reason", "examination", "treatment", "diagnoses"]);
  });

  it("no pide nada con el contenido obligatorio y un diagnóstico", () => {
    expect(
      missingNoteFields({ reason: "Control", examination: "Normal", treatment: "Profilaxis", diagnoses: [{ id: 1 }] }),
    ).toEqual([]);
  });
});

describe("correctionSchema — DS 009 art. 47 c", () => {
  it("exige campo, texto corregido y motivo", () => {
    const result = correctionSchema.safeParse({ field: "", correctedText: " ", reason: "" });
    expect(result.error?.issues.map((i) => i.path[0]).sort()).toEqual(["correctedText", "field", "reason"]);
  });

  it("no admite un diagnóstico como campo", () => {
    expect(correctionSchema.safeParse({ field: "DIAGNOSES", correctedText: "K02", reason: "error" }).success).toBe(false);
  });
});

describe("backgroundAltaSchema", () => {
  it("exige categoría y descripción", () => {
    const result = backgroundAltaSchema.safeParse({ category: "", description: "" });
    expect(result.error?.issues.map((i) => i.path[0]).sort()).toEqual(["category", "description"]);
  });
});
