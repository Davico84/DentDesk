import { describe, expect, it } from "vitest";
import { scheduleAppointmentSchema } from "./appointment";
import { businessHoursSchema, type BusinessHoursValues } from "./businessHours";

const valid = {
  patientId: "42",
  dentistUserId: "7",
  date: "2030-12-03",
  time: "10:00",
  durationMinutes: "30",
  reason: "",
};

function firstError(values: unknown, schema = scheduleAppointmentSchema) {
  const result = schema.safeParse(values);
  return result.success ? undefined : result.error.issues[0];
}

describe("scheduleAppointmentSchema", () => {
  it("acepta una cita en la grilla con duración válida", () => {
    expect(scheduleAppointmentSchema.safeParse(valid).success).toBe(true);
  });

  it.each(["7", "0", "300"])("rechaza la duración %s minutos (paridad con el backend)", (minutes) => {
    expect(firstError({ ...valid, durationMinutes: minutes })?.path).toEqual(["durationMinutes"]);
  });

  it("rechaza una hora fuera de la grilla de 5 minutos", () => {
    expect(firstError({ ...valid, time: "10:03" })?.path).toEqual(["time"]);
  });

  it("exige odontólogo y paciente", () => {
    expect(firstError({ ...valid, dentistUserId: "" })?.path).toEqual(["dentistUserId"]);
    expect(firstError({ ...valid, patientId: "" })?.path).toEqual(["patientId"]);
  });

  it("limita el motivo a 500 caracteres", () => {
    expect(firstError({ ...valid, reason: "x".repeat(501) })?.path).toEqual(["reason"]);
  });
});

function week(monday: BusinessHoursValues["days"][number]["segments"]): BusinessHoursValues {
  const days = (["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const).map((day) => ({
    day,
    segments: day === "MONDAY" ? monday : [],
  }));
  return { days };
}

describe("businessHoursSchema", () => {
  it("acepta un turno partido y tramos contiguos", () => {
    expect(businessHoursSchema.safeParse(week([
      { opens: "09:00", closes: "13:00" },
      { opens: "15:00", closes: "19:00" },
    ])).success).toBe(true);
    expect(businessHoursSchema.safeParse(week([
      { opens: "09:00", closes: "13:00" },
      { opens: "13:00", closes: "15:00" },
    ])).success).toBe(true);
  });

  it("rechaza apertura posterior o igual al cierre", () => {
    const issue = firstError(week([{ opens: "14:00", closes: "10:00" }]), businessHoursSchema as never);
    expect(issue?.path).toEqual(["days", 0, "segments", 0, "closes"]);
  });

  it("rechaza tramos que se solapan", () => {
    const issue = firstError(
      week([{ opens: "09:00", closes: "13:00" }, { opens: "12:00", closes: "15:00" }]),
      businessHoursSchema as never,
    );
    expect(issue?.message).toMatch(/solapa/);
  });

  it("rechaza un día repetido (paridad con el backend)", () => {
    const repeated = week([]);
    repeated.days[6] = { day: "MONDAY", segments: [] };
    expect(businessHoursSchema.safeParse(repeated).success).toBe(false);
  });

  it("rechaza una hora fuera de la grilla", () => {
    expect(businessHoursSchema.safeParse(week([{ opens: "09:07", closes: "12:00" }])).success).toBe(false);
  });
});
