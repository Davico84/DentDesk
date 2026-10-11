import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

// Scenario "Reglas de reenvío" (cloud-deployment, add-cloud-deployment D4): el vercel.json de la
// raíz del repo. Solo /api/* va al backend; todo lo demás sirve la app (incluidas /auth/login y
// /patients/1, que son pantallas).
interface VercelConfig {
  installCommand: string;
  buildCommand: string;
  outputDirectory: string;
  rewrites: { source: string; destination: string }[];
}

const config = JSON.parse(readFileSync(resolve(__dirname, "../../../../../../vercel.json"), "utf-8")) as VercelConfig;

describe("vercel.json", () => {
  it("reenvía /api/* al backend antes del fallback de la app", () => {
    expect(config.rewrites.map((r) => r.source)).toEqual(["/api/:path*", "/(.*)"]);
    expect(config.rewrites[0].destination).toMatch(/^https:\/\/[a-z0-9-]+\.onrender\.com\/api\/:path\*$/);
    expect(config.rewrites[1].destination).toBe("/index.html");
  });

  it("no reenvía /auth/* ni /actuator/*: son pantallas o no se exponen", () => {
    const sources = config.rewrites.map((r) => r.source);
    expect(sources.some((s) => s.startsWith("/auth"))).toBe(false);
    expect(sources.some((s) => s.startsWith("/actuator"))).toBe(false);
  });

  it("construye el paquete real con el lockfile de la raíz y publica su dist", () => {
    expect(config.installCommand).toBe("pnpm install --frozen-lockfile");
    expect(config.buildCommand).toBe("pnpm --filter dentdesk-frontend build");
    expect(config.outputDirectory).toBe("modules/frontend/dist");
  });
});
