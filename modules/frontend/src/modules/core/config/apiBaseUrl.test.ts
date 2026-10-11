import { describe, expect, it } from "vitest";
import { resolveApiBaseUrl } from "./apiBaseUrl";

// Scenario "URL base por entorno" (cloud-deployment).
describe("resolveApiBaseUrl", () => {
  it("en desarrollo, sin VITE_API_URL, usa el backend local bajo /api", () => {
    expect(resolveApiBaseUrl({ DEV: true })).toBe("http://localhost:8080/api");
  });

  it("en producción, sin VITE_API_URL, usa /api en el mismo origen", () => {
    expect(resolveApiBaseUrl({ DEV: false })).toBe("/api");
  });

  it("con VITE_API_URL usa ese valor en los dos entornos", () => {
    expect(resolveApiBaseUrl({ DEV: true, VITE_API_URL: "https://api.ejemplo.test/api" })).toBe(
      "https://api.ejemplo.test/api",
    );
    expect(resolveApiBaseUrl({ DEV: false, VITE_API_URL: "https://api.ejemplo.test/api" })).toBe(
      "https://api.ejemplo.test/api",
    );
  });

  it("una VITE_API_URL vacía cuenta como no definida", () => {
    expect(resolveApiBaseUrl({ DEV: false, VITE_API_URL: "" })).toBe("/api");
  });
});
