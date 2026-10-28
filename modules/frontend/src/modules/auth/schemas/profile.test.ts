import { describe, expect, it } from "vitest";
import { changePasswordSchema, profileSchema } from "./profile";

describe("profileSchema", () => {
  it("recorta los espacios de los extremos antes de medir", () => {
    expect(profileSchema.parse({ fullName: "  Ana  " })).toEqual({ fullName: "Ana" });
    expect(profileSchema.safeParse({ fullName: `  ${"a".repeat(120)}  ` }).success).toBe(true);
  });

  it.each(["A", "   ", "", "a".repeat(121)])("rechaza %j", (fullName) => {
    expect(profileSchema.safeParse({ fullName }).success).toBe(false);
  });
});

describe("changePasswordSchema", () => {
  const valid = { currentPassword: "actual-123", newPassword: "nueva-4567", confirmPassword: "nueva-4567" };

  it("acepta un cambio válido", () => {
    expect(changePasswordSchema.safeParse(valid).success).toBe(true);
  });

  it.each([
    ["sin la actual", { currentPassword: "" }, "currentPassword"],
    ["una nueva de menos de 8", { newPassword: "corta12", confirmPassword: "corta12" }, "newPassword"],
    ["una confirmación distinta", { confirmPassword: "otra-4567" }, "confirmPassword"],
    ["una nueva igual a la actual", { newPassword: "actual-123", confirmPassword: "actual-123" }, "newPassword"],
  ])("rechaza %s", (_, override, field) => {
    const result = changePasswordSchema.safeParse({ ...valid, ...override });
    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path[0])).toContain(field);
  });
});
