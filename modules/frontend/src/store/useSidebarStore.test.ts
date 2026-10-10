import { afterEach, describe, expect, it, vi } from "vitest";
import { useSidebarStore } from "./useSidebarStore";

afterEach(() => {
  vi.restoreAllMocks();
  useSidebarStore.setState({ collapsed: false });
  localStorage.clear();
});

describe("useSidebarStore (add-app-shell design D5)", () => {
  // Scenario: La preferencia se recuerda
  it("guarda la preferencia en el navegador", () => {
    useSidebarStore.getState().toggle();

    expect(useSidebarStore.getState().collapsed).toBe(true);
    expect(JSON.parse(localStorage.getItem("odr-sidebar")!).state.collapsed).toBe(true);
  });

  // Scenario: Sin almacenamiento disponible
  it("sin almacenamiento, se contrae y expande igual en memoria", () => {
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("bloqueado", "SecurityError");
    });

    useSidebarStore.getState().toggle();
    expect(useSidebarStore.getState().collapsed).toBe(true);
    useSidebarStore.getState().toggle();
    expect(useSidebarStore.getState().collapsed).toBe(false);
  });
});
