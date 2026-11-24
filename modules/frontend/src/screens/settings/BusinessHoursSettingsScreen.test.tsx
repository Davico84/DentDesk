import { beforeEach, describe, expect, it } from "vitest";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { BusinessHoursSettingsScreen } from "./BusinessHoursSettingsScreen";

// Scenarios de "Horario de atención configurable por el administrador" en la interfaz (add-appointments 5.1). Que un
// MEMBER no vea la sección lo cubre `sections.test.ts`.
const DEFAULT_HOURS = {
  timezone: "America/Lima",
  days: [
    { day: "MONDAY", segments: [{ opens: "09:00", closes: "18:00" }] },
    { day: "TUESDAY", segments: [{ opens: "09:00", closes: "18:00" }] },
    { day: "WEDNESDAY", segments: [{ opens: "09:00", closes: "18:00" }] },
    { day: "THURSDAY", segments: [{ opens: "09:00", closes: "18:00" }] },
    { day: "FRIDAY", segments: [{ opens: "09:00", closes: "18:00" }] },
    { day: "SATURDAY", segments: [{ opens: "09:00", closes: "13:00" }] },
    { day: "SUNDAY", segments: [] },
  ],
};

let saved: { days: Array<{ day: string; segments: Array<{ opens: string; closes: string }> }> } | null;

beforeEach(() => {
  saved = null;
  server.use(
    http.get("*/business-hours", () => HttpResponse.json(DEFAULT_HOURS)),
    http.put("*/business-hours", async ({ request }) => {
      saved = (await request.json()) as typeof saved;
      return HttpResponse.json({ timezone: "America/Lima", ...saved });
    }),
  );
});

/** Un `<input type="time">` se elige entero, no se tipea carácter por carácter. */
function setTime(input: HTMLElement, value: string) {
  fireEvent.change(input, { target: { value } });
  fireEvent.blur(input);
}

describe("BusinessHoursSettingsScreen", () => {
  it("guarda un turno partido", async () => {
    const user = userEvent.setup();
    renderWithProviders(<BusinessHoursSettingsScreen />);
    const monday = await screen.findByRole("listitem", { name: "Lunes" });

    setTime(within(monday).getByLabelText("Lunes, cierre del tramo 1"), "13:00");
    await user.click(within(monday).getByRole("button", { name: "Agregar tramo" }));
    setTime(within(monday).getByLabelText("Lunes, apertura del tramo 2"), "15:00");
    setTime(within(monday).getByLabelText("Lunes, cierre del tramo 2"), "19:00");
    await user.click(screen.getByRole("button", { name: "Guardar horario" }));

    await waitFor(() => expect(saved).not.toBeNull());
    expect(saved?.days[0]).toEqual({
      day: "MONDAY",
      segments: [{ opens: "09:00", closes: "13:00" }, { opens: "15:00", closes: "19:00" }],
    });
  });

  it("bloquea tramos que se solapan sin enviar nada", async () => {
    const user = userEvent.setup();
    renderWithProviders(<BusinessHoursSettingsScreen />);
    const monday = await screen.findByRole("listitem", { name: "Lunes" });

    await user.click(within(monday).getByRole("button", { name: "Agregar tramo" }));
    setTime(within(monday).getByLabelText("Lunes, apertura del tramo 2"), "12:00");
    setTime(within(monday).getByLabelText("Lunes, cierre del tramo 2"), "15:00");
    await user.click(screen.getByRole("button", { name: "Guardar horario" }));

    expect(await within(monday).findByText("Se solapa con otro tramo del mismo día")).toBeInTheDocument();
    expect(saved).toBeNull();
  });

  it("bloquea una apertura que no es anterior al cierre", async () => {
    const user = userEvent.setup();
    renderWithProviders(<BusinessHoursSettingsScreen />);
    const tuesday = await screen.findByRole("listitem", { name: "Martes" });

    setTime(within(tuesday).getByLabelText("Martes, apertura del tramo 1"), "14:00");
    setTime(within(tuesday).getByLabelText("Martes, cierre del tramo 1"), "10:00");
    await user.click(screen.getByRole("button", { name: "Guardar horario" }));

    expect(await within(tuesday).findByText("El cierre debe ser posterior a la apertura")).toBeInTheDocument();
    expect(saved).toBeNull();
  });

  it("un día sin tramos se muestra cerrado", async () => {
    renderWithProviders(<BusinessHoursSettingsScreen />);

    expect(within(await screen.findByRole("listitem", { name: "Domingo" })).getByText("Cerrado")).toBeInTheDocument();
  });
});
