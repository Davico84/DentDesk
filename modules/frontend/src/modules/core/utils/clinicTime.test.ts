import { describe, expect, it } from "vitest";
import { addDays, clinicLocalToInstant, instantToClinicLocal, isoWeekday, mondayOf } from "./clinicTime";

const LIMA = "America/Lima";
/** Zona con horario de verano: el 10/03/2030 salta de 02:00 a 03:00; el 03/11/2030 repite 01:00–02:00. */
const NEW_YORK = "America/New_York";

describe("clinicTime", () => {
  it("convierte una hora de Lima al instante UTC y de vuelta", () => {
    const resolved = clinicLocalToInstant("2030-12-03", "10:00", LIMA);

    expect(resolved.instant.toISOString()).toBe("2030-12-03T15:00:00.000Z");
    expect(resolved.adjusted).toBe(false);
    expect(instantToClinicLocal("2030-12-03T15:00:00Z", LIMA)).toEqual({
      date: "2030-12-03",
      time: "10:00",
      offset: "-05:00",
    });
  });

  it("muestra la hora de la clínica sin importar la zona del equipo", () => {
    // El resultado depende solo de la zona pasada, nunca de la del navegador (scenario "Navegador en otra zona").
    const tokyo = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Tokyo", hour: "2-digit", hourCycle: "h23" })
      .format(new Date("2030-12-03T15:00:00Z"));
    expect(tokyo).toBe("00");
    expect(instantToClinicLocal(new Date("2030-12-03T15:00:00Z"), LIMA).time).toBe("10:00");
  });

  it("avanza una hora inexistente al primer instante válido y lo marca como ajustada", () => {
    const resolved = clinicLocalToInstant("2030-03-10", "02:30", NEW_YORK);

    expect(resolved.adjusted).toBe(true);
    expect(resolved.instant.toISOString()).toBe("2030-03-10T07:00:00.000Z");
    expect(resolved.local).toEqual({ date: "2030-03-10", time: "03:00", offset: "-04:00" });
  });

  it("resuelve una hora repetida a su primera ocurrencia", () => {
    const resolved = clinicLocalToInstant("2030-11-03", "01:30", NEW_YORK);

    expect(resolved.adjusted).toBe(false);
    expect(resolved.instant.toISOString()).toBe("2030-11-03T05:30:00.000Z");
    expect(resolved.local.offset).toBe("-04:00");
  });

  it("calcula el lunes de la semana y suma días de calendario", () => {
    expect(isoWeekday("2030-12-08")).toBe(7);
    expect(mondayOf("2030-12-08")).toBe("2030-12-02");
    expect(mondayOf("2030-12-02")).toBe("2030-12-02");
    expect(addDays("2030-12-30", 3)).toBe("2031-01-02");
  });
});
