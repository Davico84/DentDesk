import { describe, expect, it } from "vitest";
import { canAccess, sectionById, sections, type SectionConfig } from "./sections";
import { navItemsFor } from "@/modules/core/components/shell/navItems";

describe("sections — una sola fuente de verdad (add-app-shell)", () => {
  it("ids y rutas son únicos: una ruta no puede tener dos políticas", () => {
    expect(new Set(sections.map((s) => s.id)).size).toBe(sections.length);
    expect(new Set(sections.map((s) => s.path)).size).toBe(sections.length);
  });

  // Scenario: Ítem sin roles visible para todos
  it("una sección sin roles la ven todos; con roles, solo esos", () => {
    expect(canAccess(sectionById("patients"), "USER")).toBe(true);
    expect(canAccess(sectionById("patients"), "ADMIN")).toBe(true);
    expect(canAccess(sectionById("branding"), "USER")).toBe(false);
    expect(canAccess(sectionById("branding"), "ADMIN")).toBe(true);
  });

  it("los roles salen del contrato: uno mal escrito no compila", () => {
    // @ts-expect-error "ADMN" no es un UserResponseRole
    const typo: SectionConfig = { id: "branding", path: "/x", roles: ["ADMN"] };
    expect(typo).toBeDefined();
  });

  // Scenario: Menú y rutas derivados de la misma configuración
  it("el menú filtra por rol y toma la ruta de la sección", () => {
    expect(navItemsFor("USER").map((i) => i.label)).toEqual(["Inicio", "Pacientes"]);
    expect(navItemsFor("ADMIN").map((i) => [i.label, i.to])).toEqual([
      ["Inicio", "/"],
      ["Pacientes", "/patients"],
      ["Marca", "/settings/branding"],
    ]);
  });
});
