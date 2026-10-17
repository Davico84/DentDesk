import { beforeEach, describe, expect, it } from "vitest";
import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { useGuidanceStore } from "@/store/useGuidanceStore";
import { DentalChartEditor } from "../components/DentalChartEditor";
import { chartBlock, dentalChartHandlers } from "../test/dentalChartMocks";

beforeEach(() => {
  useGuidanceStore.setState({ seen: {} });
  useSessionStore.setState({
    accessToken: "tok",
    status: "authenticated",
    user: { id: 7, email: "ana@clinica.test", fullName: "Ana", role: "ADMIN", organizationId: 1, organizationName: "C", organizationSlug: "c" },
  });
});

// Montan el odontograma completo (SVG grande): con la suite entera en paralelo tarda más que los plazos por defecto.
const SLOW = { timeout: 5_000 };
const guide = () => screen.findByRole("dialog", { name: "¿Dónde registro un hallazgo?" }, SLOW);

describe("Guía del odontograma", { timeout: 20_000 }, () => {
  it("la primera vez que el usuario ve un odontograma editable, se abre sola", async () => {
    server.use(...dentalChartHandlers());
    renderWithProviders(<DentalChartEditor patientId={1} />);
    expect(await guide()).toBeInTheDocument();
    expect(screen.getByText("Paso 1 de 7")).toBeInTheDocument();
  });

  it("una vez vista no se abre sola, y el botón la vuelve a abrir", async () => {
    useGuidanceStore.getState().markSeen("dental-chart", 7);
    server.use(...dentalChartHandlers());
    const user = userEvent.setup();
    renderWithProviders(<DentalChartEditor patientId={1} />);
    const button = await screen.findByRole("button", { name: "¿Cómo registro hallazgos?" }, SLOW);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(button);
    expect(await guide()).toBeInTheDocument();
  });

  it("el paso de pieza completa resalta el número de la pieza y nombra ejemplos del catálogo", async () => {
    server.use(...dentalChartHandlers());
    const user = userEvent.setup();
    renderWithProviders(<DentalChartEditor patientId={1} />);
    await guide();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    const step = screen.getByRole("dialog", { name: "Toda la pieza" });
    expect(within(step).getByText(/Corona/)).toBeInTheDocument();
    expect(within(step).getByText(/Tratamiento de conducto/i)).toBeInTheDocument();
    expect(document.querySelector('[data-guide="tooth-number"]')).toHaveAttribute("aria-label", expect.stringMatching(/16/));
  });

  it("el paso de caras resalta la corona entera de la pieza, con sus 5 caras", async () => {
    server.use(...dentalChartHandlers());
    const user = userEvent.setup();
    renderWithProviders(<DentalChartEditor patientId={1} />);
    await guide();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(screen.getByRole("dialog", { name: "Una cara de la pieza" })).toHaveTextContent("5 caras");
    const crown = document.querySelector('[data-guide="tooth-surface"]')!;
    expect(crown.querySelectorAll('[aria-label^="Pieza 16, superficie"]')).toHaveLength(5);
  });

  it("tocar la zona resaltada no abre el registro de hallazgos", async () => {
    server.use(...dentalChartHandlers());
    const user = userEvent.setup();
    renderWithProviders(<DentalChartEditor patientId={1} />);
    await guide();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    await user.click(screen.getByTestId("tour-shield"));
    expect(screen.queryByRole("dialog", { name: /Agregar hallazgo/ })).not.toBeInTheDocument();
    expect(screen.getByRole("dialog", { name: "Toda la pieza" })).toBeInTheDocument();
  });

  it("cerrarla no registra nada y la marca como vista", async () => {
    let posted = false;
    server.events.on("request:start", ({ request }) => {
      if (request.method !== "GET") posted = true;
    });
    server.use(...dentalChartHandlers());
    const user = userEvent.setup();
    renderWithProviders(<DentalChartEditor patientId={1} />);
    await guide();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(posted).toBe(false);
    expect(useGuidanceStore.getState().hasSeen("dental-chart", 7)).toBe(true);
    server.events.removeAllListeners();
  });

  it("con el odontograma sellado no se abre ni ofrece el botón", async () => {
    server.use(...dentalChartHandlers({ block: chartBlock("SEALED") }));
    renderWithProviders(<DentalChartEditor patientId={1} />);
    expect(await screen.findByText("Examinado el 2026-10-01", {}, SLOW)).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "¿Cómo registro hallazgos?" })).not.toBeInTheDocument();
  });

  it("mientras el bloque carga no se abre (aunque luego resulte abierto, recién ahí)", async () => {
    server.use(...dentalChartHandlers({ blockDelayMs: 150 }));
    renderWithProviders(<DentalChartEditor patientId={1} />);
    await act(() => new Promise((resolve) => setTimeout(resolve, 50)));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(await guide()).toBeInTheDocument();
  });
});
