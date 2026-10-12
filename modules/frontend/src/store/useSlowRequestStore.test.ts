import { beforeEach, describe, expect, it } from "vitest";
import { useSlowRequestStore } from "./useSlowRequestStore";

const state = () => useSlowRequestStore.getState();

beforeEach(() => state().reset());

describe("useSlowRequestStore", () => {
  it("empieza sin peticiones lentas", () => {
    expect(state().slowIds.size).toBe(0);
    expect(state().slowSince).toBeNull();
  });

  it("guarda el inicio de la espera lenta más antigua", () => {
    state().markSlow(1, 1000);
    state().markSlow(2, 5000);
    expect(state().slowIds.size).toBe(2);
    expect(state().slowSince).toBe(1000);
  });

  it("sigue esperando hasta que termina la última lenta", () => {
    state().markSlow(1, 1000);
    state().markSlow(2, 2000);
    state().done(1);
    expect(state().slowIds.size).toBe(1);
    expect(state().slowSince).toBe(1000);
    state().done(2);
    expect(state().slowIds.size).toBe(0);
    expect(state().slowSince).toBeNull();
  });

  it("terminar una petición que nunca fue lenta no cambia nada", () => {
    state().markSlow(1, 1000);
    const before = state();
    state().done(99);
    expect(state()).toBe(before);
  });
});
