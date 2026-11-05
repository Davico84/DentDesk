import { describe, expect, it } from "vitest";
import { passwordRecoverySchema, resetPasswordSchema } from "./auth";

describe("passwordRecoverySchema", () => {
  it("quita los espacios de los extremos antes de validar el formato", () => {
    expect(passwordRecoverySchema.parse({ email: "  ana@clinica.test " })).toEqual({ email: "ana@clinica.test" });
  });

  it.each(["", "   ", "no-es-un-correo"])("rechaza %j", (email) => {
    expect(passwordRecoverySchema.safeParse({ email }).success).toBe(false);
  });
});

describe("resetPasswordSchema", () => {
  const valid = { newPassword: "nueva-4567", confirmPassword: "nueva-4567" };

  it("acepta una contraseña nueva válida y confirmada", () => {
    expect(resetPasswordSchema.safeParse(valid).success).toBe(true);
  });

  it.each([
    ["una nueva de menos de 8", { newPassword: "corta12", confirmPassword: "corta12" }, "newPassword"],
    ["una confirmación distinta", { confirmPassword: "otra-4567" }, "confirmPassword"],
    ["sin confirmación", { confirmPassword: "" }, "confirmPassword"],
  ])("rechaza %s", (_, override, field) => {
    const result = resetPasswordSchema.safeParse({ ...valid, ...override });
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path[0])).toContain(field);
  });
});
