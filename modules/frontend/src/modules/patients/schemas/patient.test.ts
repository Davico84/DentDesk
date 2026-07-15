import { describe, it, expect } from "vitest";
import { patientSchema, toRequest, toDefaults, isMinor, type PatientValues } from "./patient";

function isoYearsAgo(years: number): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - years);
  return d.toISOString().slice(0, 10);
}

function values(overrides: Partial<PatientValues> = {}): PatientValues {
  return {
    fullName: "Ada Lovelace",
    document: "",
    birthDate: isoYearsAgo(30),
    sex: "",
    phone: "",
    email: "",
    address: "",
    guardianName: "",
    guardianRelationship: "",
    guardianDocument: "",
    guardianPhone: "",
    emergencyContactName: "",
    emergencyContactPhone: "",
    emergencyContactRelationship: "",
    allergies: "",
    conditions: "",
    currentMedication: "",
    ...overrides,
  };
}

describe("patientSchema", () => {
  it("acepta un adulto sin responsable", () => {
    expect(patientSchema.safeParse(values()).success).toBe(true);
  });

  it("rechaza un menor sin responsable, con error por cada campo del responsable", () => {
    const result = patientSchema.safeParse(values({ birthDate: isoYearsAgo(10) }));
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path[0]);
      expect(paths).toEqual(expect.arrayContaining(["guardianName", "guardianRelationship", "guardianPhone"]));
    }
  });

  it("acepta un menor con responsable", () => {
    const result = patientSchema.safeParse(
      values({ birthDate: isoYearsAgo(10), guardianName: "María", guardianRelationship: "madre", guardianPhone: "+51999" }),
    );
    expect(result.success).toBe(true);
  });

  it("exige el nombre", () => {
    expect(patientSchema.safeParse(values({ fullName: "" })).success).toBe(false);
  });
});

describe("isMinor", () => {
  it("deriva la minoría de la fecha de nacimiento", () => {
    expect(isMinor(isoYearsAgo(10))).toBe(true);
    expect(isMinor(isoYearsAgo(30))).toBe(false);
    expect(isMinor("")).toBe(false);
  });
});

describe("toRequest", () => {
  it("convierte la anamnesis separada por comas en listas y omite los vacíos", () => {
    const request = toRequest(values({ allergies: "penicilina, látex ,", document: "  " }));
    expect(request.allergies).toEqual(["penicilina", "látex"]);
    expect(request.conditions).toEqual([]);
    expect(request.document).toBeUndefined();
  });
});

describe("toDefaults", () => {
  it("une las listas con comas para editar", () => {
    const defaults = toDefaults({
      id: 1,
      fullName: "Ada",
      birthDate: "2000-01-01",
      allergies: ["penicilina", "látex"],
    });
    expect(defaults.allergies).toBe("penicilina, látex");
    expect(defaults.fullName).toBe("Ada");
  });
});
