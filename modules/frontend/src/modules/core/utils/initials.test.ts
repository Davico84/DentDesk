import { describe, expect, it } from "vitest";
import { initialsOf } from "./initials";

describe("initialsOf", () => {
  it("toma la primera y la última palabra", () => {
    expect(initialsOf("Clínica Dental Sonrisas")).toBe("CS");
  });
  it("con una sola palabra toma sus dos primeras letras", () => {
    expect(initialsOf("odontorisas")).toBe("OD");
  });
  it("con un correo usa sus dos primeras letras", () => {
    expect(initialsOf("ana@clinica.pe")).toBe("AN");
  });
  it("sin texto devuelve un marcador", () => {
    expect(initialsOf("   ")).toBe("?");
  });
});
