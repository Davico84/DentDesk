import { describe, expect, it } from "vitest";
import { normalizeForSearch } from "./search";

describe("normalizeForSearch", () => {
  it("quita tildes, mayúsculas y espacios de los extremos", () => {
    expect(normalizeForSearch("  María PÉREZ ")).toBe("maria perez");
  });

  it("conserva la ñ como n sin tilde y las letras sin marca", () => {
    expect(normalizeForSearch("Ñandú")).toBe("nandu");
  });
});
