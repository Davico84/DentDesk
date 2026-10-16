import { beforeEach, describe, expect, it } from "vitest";
import { useGuidanceStore } from "./useGuidanceStore";

beforeEach(() => useGuidanceStore.setState({ seen: {} }));

describe("useGuidanceStore", () => {
  it("una guía no vista por nadie no figura como vista", () => {
    expect(useGuidanceStore.getState().hasSeen("dental-chart", 7)).toBe(false);
  });

  it("vista por un usuario no cuenta como vista por otro del mismo navegador", () => {
    useGuidanceStore.getState().markSeen("dental-chart", 7);
    expect(useGuidanceStore.getState().hasSeen("dental-chart", 7)).toBe(true);
    expect(useGuidanceStore.getState().hasSeen("dental-chart", 8)).toBe(false);
  });

  it("marcarla dos veces no la duplica", () => {
    useGuidanceStore.getState().markSeen("dental-chart", 7);
    useGuidanceStore.getState().markSeen("dental-chart", 7);
    expect(useGuidanceStore.getState().seen["dental-chart"]).toEqual([7]);
  });

  it("sin usuario no se marca ni se da por vista", () => {
    useGuidanceStore.getState().markSeen("dental-chart", undefined);
    expect(useGuidanceStore.getState().seen).toEqual({});
    expect(useGuidanceStore.getState().hasSeen("dental-chart", undefined)).toBe(false);
  });

  it("persiste en el almacenamiento del navegador", () => {
    useGuidanceStore.getState().markSeen("dental-chart", 7);
    expect(localStorage.getItem("odr-guidance")).toContain('"dental-chart":[7]');
  });
});
