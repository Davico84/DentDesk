import { describe, it, expect } from "vitest";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "@/test/utils";
import { HomeScreen } from "./HomeScreen";

// Scenario "Prueba base con MSW": renderiza la app con RTL (MSW activo en setup.ts).
describe("HomeScreen", () => {
  it("renderiza el título de la aplicación", () => {
    renderWithProviders(<HomeScreen />);
    expect(
      screen.getByRole("heading", { name: "OdontoRisas" }),
    ).toBeInTheDocument();
  });
});
