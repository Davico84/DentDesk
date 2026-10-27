import { describe, expect, it } from "vitest";
import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { credentialHandlers, ENABLED, EXPIRED } from "../test/credentialApiMocks";
import { ProfessionalCredentialPage } from "./ProfessionalCredentialPage";

// Scenarios de staff: Declarar la credencial profesional propia · Capacidad clínica efectiva en la clínica activa.

function renderPage() {
  return renderWithProviders(<ProfessionalCredentialPage />, { initialEntries: ["/profile/credential"] });
}

const cop = () => screen.findByLabelText(/Número de colegiatura/);
const until = () => screen.getByLabelText(/Habilitado hasta/);
const sworn = () => screen.getByRole("checkbox", { name: /Declaro bajo juramento/ });

describe("ProfessionalCredentialPage", () => {
  it("sin credencial, explica por qué no se puede firmar", async () => {
    server.use(...credentialHandlers().handlers);
    renderPage();
    expect(await screen.findByText("Sin credencial")).toBeInTheDocument();
    expect(screen.getByText("Para firmar actos clínicos, declara tu credencial profesional.")).toBeInTheDocument();
  });

  it("declarar por primera vez envía la credencial y queda habilitado", async () => {
    const { handlers, calls } = credentialHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderPage();
    await user.type(await cop(), "54321");
    await user.type(until(), "2099-12-31");
    await user.click(sworn());
    await user.click(screen.getByRole("button", { name: "Declarar credencial" }));

    expect(await screen.findByText(/^Habilitado hasta 31 dic\.? 2099$/)).toBeInTheDocument();
    expect(calls.puts).toEqual([{ copNumber: "54321", licensedUntil: "2099-12-31", sworn: true }]);
  });

  it("con la habilitación vencida, la muestra con su fecha y permite renovarla", async () => {
    const { handlers, calls } = credentialHandlers({ current: EXPIRED });
    server.use(...handlers);
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText(/^Habilitación vencida el 20 oct\.? 2026$/)).toBeInTheDocument();
    // El formulario trae lo declarado: renovar es cambiar la fecha y volver a jurar.
    expect(await cop()).toHaveValue("12345");
    await user.clear(until());
    await user.type(until(), "2099-06-30");
    await user.click(sworn());
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByText(/^Habilitado hasta 30 jun\.? 2099$/)).toBeInTheDocument();
    expect(calls.puts[0]?.licensedUntil).toBe("2099-06-30");
  });

  it("no envía nada sin la declaración jurada", async () => {
    const { handlers, calls } = credentialHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderPage();
    await user.type(await cop(), "54321");
    await user.type(until(), "2099-12-31");
    await user.click(screen.getByRole("button", { name: "Declarar credencial" }));

    expect(await screen.findByText("Debes aceptar la declaración jurada")).toBeInTheDocument();
    expect(calls.puts).toHaveLength(0);
  });

  it("rechaza un número con letras y una fecha pasada sin llamar al servidor", async () => {
    const { handlers, calls } = credentialHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderPage();
    await user.type(await cop(), "12A45");
    await user.type(until(), "2000-01-01");
    await user.click(sworn());
    await user.click(screen.getByRole("button", { name: "Declarar credencial" }));

    expect(await screen.findByText("Solo dígitos, hasta 10")).toBeInTheDocument();
    expect(screen.getByText("La fecha debe ser hoy o posterior")).toBeInTheDocument();
    expect(calls.puts).toHaveLength(0);
  });

  it("un número de otra cuenta se muestra en su campo", async () => {
    server.use(...credentialHandlers({ current: ENABLED, putStatus: 409, putType: "/errors/credential-number-taken" }).handlers);
    const user = userEvent.setup();
    renderPage();
    const field = await cop();
    await user.clear(field);
    await user.type(field, "99999");
    await user.click(sworn());
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByText("Ese número de colegiatura ya está registrado en otra cuenta.")).toBeInTheDocument();
    expect(field).toHaveAttribute("aria-invalid", "true");
  });

  it("una fecha que el servidor da por pasada (hora de Perú) se muestra en su campo", async () => {
    server.use(...credentialHandlers({ current: ENABLED, putStatus: 400, putType: "/errors/licensed-until-in-past" }).handlers);
    const user = userEvent.setup();
    renderPage();
    await cop();
    await user.click(sworn());
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }));

    expect(await screen.findByText("La fecha de habilitación debe ser hoy o posterior.")).toBeInTheDocument();
    expect(until()).toHaveAttribute("aria-invalid", "true");
  });
});
