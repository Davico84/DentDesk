import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { useGuidanceStore } from "@/store/useGuidanceStore";
import type { DentalChartFindingResponse } from "@/modules/core/services/generated/model";
import { DentalChartEditor } from "../components/DentalChartEditor";
import { chartBlock, dentalChartHandlers } from "../test/dentalChartMocks";

// Montan el odontograma completo (SVG grande): con la suite entera en paralelo tarda más que los plazos por defecto.
const SLOW = { timeout: 5_000 };
let writes = 0;

beforeEach(() => {
  writes = 0;
  server.events.on("request:start", ({ request }) => {
    if (request.method !== "GET") writes += 1;
  });
  // La guía ya vista: estos tests son del registro, no del recorrido.
  useGuidanceStore.setState({ seen: { "dental-chart": [7] } });
  useSessionStore.setState({
    accessToken: "tok",
    status: "authenticated",
    user: { id: 7, email: "ana@clinica.test", fullName: "Ana", role: "ADMIN", organizationId: 1, organizationName: "C", organizationSlug: "c" },
  });
});

afterEach(() => {
  server.events.removeAllListeners();
});

type User = ReturnType<typeof userEvent.setup>;

async function renderEditor(findings: DentalChartFindingResponse[] = []) {
  server.use(...dentalChartHandlers({ block: chartBlock("OPEN", findings) }));
  const user = userEvent.setup({ delay: null });
  renderWithProviders(<DentalChartEditor patientId={1} />);
  await screen.findByRole("button", { name: "Buscar hallazgo" }, SLOW);
  await waitFor(() => expect(screen.getByRole("button", { name: "Buscar hallazgo" })).toBeEnabled(), SLOW);
  return user;
}

async function touch(user: User, name: string | RegExp) {
  await user.click(screen.getByRole("button", { name }));
  return screen.findByRole("dialog", {}, SLOW);
}

const typeBox = () => screen.getByRole("combobox", { name: /tipo de hallazgo/i });

async function chooseType(user: User, text: string, option: RegExp) {
  await user.type(typeBox(), text);
  await user.click(screen.getByRole("option", { name: option }));
}

describe("Búsqueda de hallazgos por nombre y modo ubicar (design D5)", { timeout: 20_000 }, () => {
  it("el tipo buscado va en la zona tocada: aparece en \"Para esta zona\" y queda elegido", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 16, superficie oclusal, sin hallazgos");
    await user.type(typeBox(), "caries");
    expect(within(screen.getByRole("group", { name: "Para esta zona" })).getByRole("option", { name: "Lesión de caries" })).toBeInTheDocument();
    await user.click(screen.getByRole("option", { name: "Lesión de caries" }));
    expect(typeBox()).toHaveValue("Lesión de caries");
    expect(screen.getByRole("dialog")).toHaveAccessibleDescription("Pieza 16, superficie oclusal");
  });

  it("elige el tipo con el teclado, y Escape con la lista abierta cierra solo la lista", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 16, superficie oclusal, sin hallazgos");
    await user.type(typeBox(), "sell");
    expect(typeBox()).toHaveAttribute("aria-expanded", "true");
    await user.keyboard("{Escape}");
    expect(typeBox()).toHaveAttribute("aria-expanded", "false");
    expect(screen.getByRole("dialog")).toBeInTheDocument();

    await user.type(typeBox(), "sell");
    await user.keyboard("{ArrowDown}{ArrowUp}{Enter}");
    expect(typeBox()).toHaveValue("Sellante");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("desde una cara de la 16, corona ofrece la pieza completa y abre su formulario aunque la 16 tenga hallazgos", async () => {
    const user = await renderEditor([
      { id: 9, blockId: 1, type: "TRATAMIENTO_CONDUCTO", location: { locationKind: "TOOTH", toothA: 16 } },
    ]);
    await touch(user, "Pieza 16, superficie oclusal, sin hallazgos");
    await chooseType(user, "corona", /^Corona/);
    expect(screen.getByRole("status")).toHaveTextContent("Corona se registra sobre la pieza completa");
    await user.click(screen.getByRole("button", { name: "Registrarlo en la pieza 16" }));
    await waitFor(() => expect(screen.getByRole("dialog")).toHaveAccessibleDescription("Pieza 16"), SLOW);
    expect(screen.getByRole("heading", { name: "Agregar hallazgo" })).toBeInTheDocument();
    expect(typeBox()).toHaveValue("Corona");
  });

  it("desde la 36, un hallazgo de arco abre el registro del arco inferior con ese tipo", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 36, sin hallazgos");
    await chooseType(user, "protesis comp", /^Prótesis completa/);
    await user.click(screen.getByRole("button", { name: "Registrarlo en el arco inferior" }));
    await waitFor(() => expect(screen.getByRole("dialog")).toHaveAccessibleDescription("Arco inferior"), SLOW);
    expect(typeBox()).toHaveValue("Prótesis completa");
  });

  it("desde la 14, un hallazgo de rango empieza el rango en ella y solo falta la última pieza", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 14, sin hallazgos");
    await chooseType(user, "fija", /^Prótesis parcial fija/);
    await user.click(screen.getByRole("button", { name: "Empezar un rango desde la pieza 14" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument(), SLOW);
    expect(screen.getByText("Ubicando: Prótesis parcial fija")).toBeInTheDocument();
    expect(screen.getByText(/Primera pieza:/)).toHaveTextContent("Primera pieza: 14");
    await touch(user, "Pieza 17, sin hallazgos");
    expect(screen.getByRole("dialog")).toHaveAccessibleDescription(/^Rango: piezas .*14/);
    expect(typeBox()).toHaveValue("Prótesis parcial fija");
  });

  it("re-anclar el rango en otra fila conserva el hallazgo elegido", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 14, sin hallazgos");
    await chooseType(user, "fija", /^Prótesis parcial fija/);
    await user.click(screen.getByRole("button", { name: "Empezar un rango desde la pieza 14" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument(), SLOW);
    await user.click(screen.getByRole("button", { name: "Pieza 44, sin hallazgos" }));
    expect(screen.getByText(/Primera pieza:/)).toHaveTextContent("Primera pieza: 44");
    await touch(user, "Pieza 42, sin hallazgos");
    expect(typeBox()).toHaveValue("Prótesis parcial fija");
  });

  it("destino que no se deduce: \"Elegir en el odontograma\", solo responden las caras y el registro trae el tipo", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 36, sin hallazgos");
    await chooseType(user, "sellante", /^Sellante/);
    expect(screen.getByRole("status")).toHaveTextContent("Sellante se registra sobre una cara de la pieza");
    await user.click(screen.getByRole("button", { name: "Elegir en el odontograma" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument(), SLOW);
    expect(screen.getByText("Ubicando: Sellante")).toBeInTheDocument();
    expect(screen.getByText(/Toca una cara de la pieza 36\./)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Pieza 36, sin hallazgos" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    await touch(user, "Pieza 36, superficie oclusal, sin hallazgos");
    expect(screen.getByRole("dialog")).toHaveAccessibleDescription("Pieza 36, superficie oclusal");
    expect(typeBox()).toHaveValue("Sellante");
    expect(screen.queryByText("Ubicando: Sellante")).not.toBeInTheDocument();
  });

  it("cancelar el modo ubicar no registra nada y el siguiente registro no trae el hallazgo", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 36, sin hallazgos");
    await chooseType(user, "diastema", /^Diastema/);
    await user.click(screen.getByRole("button", { name: "Elegir en el odontograma" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument(), SLOW);
    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(screen.queryByText("Ubicando: Diastema")).not.toBeInTheDocument();

    await touch(user, "Entre las piezas 36 y 37");
    expect(typeBox()).toHaveValue("");
    expect(writes).toBe(0);
  });

  it("Escape también sale del modo ubicar sin registrar nada", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 36, sin hallazgos");
    await chooseType(user, "sellante", /^Sellante/);
    await user.click(screen.getByRole("button", { name: "Elegir en el odontograma" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument(), SLOW);
    await user.keyboard("{Escape}");
    expect(screen.queryByText("Ubicando: Sellante")).not.toBeInTheDocument();
    await touch(user, "Pieza 36, superficie oclusal, sin hallazgos");
    expect(typeBox()).toHaveValue("");
    expect(writes).toBe(0);
  });

  it("\"Buscar hallazgo\" en la barra: elegir un tipo activa el modo ubicar para ese tipo", async () => {
    const user = await renderEditor();
    await user.click(screen.getByRole("button", { name: "Buscar hallazgo" }));
    const search = await screen.findByRole("dialog", { name: "Buscar hallazgo" });
    await user.type(within(search).getByRole("combobox", { name: "Hallazgo" }), "diaste");
    await user.click(screen.getByRole("option", { name: "Diastema" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument(), SLOW);
    expect(screen.getByText("Ubicando: Diastema")).toBeInTheDocument();

    await touch(user, "Entre las piezas 11 y 21");
    expect(screen.getByRole("dialog")).toHaveAccessibleDescription("Entre las piezas 11 y 21");
    expect(typeBox()).toHaveValue("Diastema");
  });

  it("durante el modo ubicar, la guía y \"Buscar hallazgo\" no están disponibles", async () => {
    const user = await renderEditor();
    await user.click(screen.getByRole("button", { name: /registrar rango/i }));
    expect(screen.getByRole("button", { name: "¿Cómo registro hallazgos?" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Buscar hallazgo" })).toBeDisabled();
  });

  it("una búsqueda sin resultados lo dice y conserva lo ya completado", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 36, sin hallazgos");
    await chooseType(user, "corona", /^Corona/);
    await user.clear(typeBox());
    await user.type(typeBox(), "xyz");
    expect(screen.getByRole("status")).toHaveTextContent("No hay ningún hallazgo con ese nombre");
    await user.keyboard("{Escape}");
    expect(typeBox()).toHaveValue("Corona");
  });

  it("si no acepta ir a otra zona, la tocada se mantiene y no se registra nada", async () => {
    const user = await renderEditor();
    await touch(user, "Pieza 16, superficie oclusal, sin hallazgos");
    await chooseType(user, "corona", /^Corona/);
    expect(screen.getByRole("button", { name: "Registrarlo en la pieza 16" })).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toHaveAccessibleDescription("Pieza 16, superficie oclusal");
    expect(typeBox()).toHaveValue("");
    expect(writes).toBe(0);
  });

  it("en edición solo se listan los tipos de la zona actual", async () => {
    const user = await renderEditor([
      { id: 5, blockId: 1, type: "SELLANTE", location: { locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" } },
    ]);
    await touch(user, /Pieza 36, superficie oclusal: 1 hallazgo/);
    await user.click(screen.getByRole("button", { name: "Editar" }));
    await user.click(typeBox());
    expect(screen.getByRole("group", { name: "Para esta zona" })).toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "En otras zonas" })).not.toBeInTheDocument();
  });
});
