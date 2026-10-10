import { afterEach, describe, expect, it, vi } from "vitest";
import { clinicDay, clinicToday } from "./clinicDate";

describe("clinicDate", () => {
  afterEach(() => vi.useRealTimers());

  it("a las 19:35 en Lima el día de la clínica sigue siendo hoy, aunque en UTC ya sea mañana", () => {
    const instant = new Date("2026-10-10T00:35:35Z");
    expect(instant.toISOString().slice(0, 10)).toBe("2026-10-10");
    expect(clinicDay(instant)).toBe("2026-10-09");
  });

  it("clinicToday usa el reloj actual en la zona de la clínica", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-10T00:35:35Z"));
    expect(clinicToday()).toBe("2026-10-09");
  });
});
