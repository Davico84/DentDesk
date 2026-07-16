import { describe, it, expect } from "vitest";
import { patientSchema, toRequest, toDefaults, isMinor, documentMaxLength, type PatientValues } from "./patient";

function isoYearsAgo(years: number): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() - years);
  return d.toISOString().slice(0, 10);
}

function values(overrides: Partial<PatientValues> = {}): PatientValues {
  return {
    fullName: "Ada Lovelace",
    documentType: "",
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
      values({ birthDate: isoYearsAgo(10), guardianName: "María", guardianRelationship: "MOTHER", guardianPhone: "+51999888" }),
    );
    expect(result.success).toBe(true);
  });

  it("exige el nombre", () => {
    expect(patientSchema.safeParse(values({ fullName: "" })).success).toBe(false);
  });
});

/** Paridad con el @PastOrPresent del backend: la edad DERIVA de esta fecha. */
describe("patientSchema — fecha de nacimiento", () => {
  it("rechaza una fecha futura", () => {
    const future = new Date();
    future.setFullYear(future.getFullYear() + 1);
    const result = patientSchema.safeParse(values({ birthDate: future.toISOString().slice(0, 10) }));
    expect(result.success).toBe(false);
  });

  it("acepta hoy (un recién nacido) — que, siendo menor, necesita responsable", () => {
    const result = patientSchema.safeParse(
      values({
        birthDate: new Date().toISOString().slice(0, 10),
        guardianName: "María",
        guardianRelationship: "MOTHER",
        guardianPhone: "+51999888",
      }),
    );
    expect(result.success).toBe(true);
  });

  it("rechaza una fecha inválida", () => {
    expect(patientSchema.safeParse(values({ birthDate: "no-es-fecha" })).success).toBe(false);
  });
});

/** Paridad con el backend (design.md D8): emparejamiento tipo↔número y formato según el tipo. */
describe("patientSchema — documento", () => {
  function pathsInError(v: PatientValues): unknown[] {
    const result = patientSchema.safeParse(v);
    return result.success ? [] : result.error.issues.map((i) => i.path[0]);
  }

  it("acepta no tener documento (es opcional)", () => {
    expect(patientSchema.safeParse(values()).success).toBe(true);
  });

  it("acepta un DNI válido de 8 dígitos", () => {
    expect(patientSchema.safeParse(values({ documentType: "DNI", document: "12345678" })).success).toBe(true);
  });

  it("exige el tipo si se da el número", () => {
    expect(pathsInError(values({ document: "12345678" }))).toContain("documentType");
  });

  it("exige el número si se da el tipo", () => {
    expect(pathsInError(values({ documentType: "DNI" }))).toContain("document");
  });

  it("rechaza un DNI con formato inválido", () => {
    expect(pathsInError(values({ documentType: "DNI", document: "123" }))).toContain("document");
    expect(pathsInError(values({ documentType: "DNI", document: "1234567X" }))).toContain("document");
  });

  it("rechaza un DNI de MÁS de 8 dígitos (pasarse también es inválido)", () => {
    expect(pathsInError(values({ documentType: "DNI", document: "123456789" }))).toContain("document");
  });

  it("el largo máximo depende del tipo: impide pasarse en vez de avisar después", () => {
    expect(documentMaxLength("DNI")).toBe(8);
    expect(documentMaxLength("CE")).toBe(12);
    expect(documentMaxLength("PASSPORT")).toBe(12);
    expect(documentMaxLength("")).toBeUndefined();
  });

  it("acepta un pasaporte alfanumérico", () => {
    expect(patientSchema.safeParse(values({ documentType: "PASSPORT", document: "AB123456" })).success).toBe(true);
  });
});

describe("patientSchema — teléfono", () => {
  it("rechaza letras en el teléfono", () => {
    expect(patientSchema.safeParse(values({ phone: "no-es-un-numero" })).success).toBe(false);
  });

  it("acepta dígitos, «+» y espacios", () => {
    expect(patientSchema.safeParse(values({ phone: "+51 987 654 321" })).success).toBe(true);
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
