import { beforeEach, describe, expect, it } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Toaster } from "sonner";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import { useSessionStore } from "@/store/useSessionStore";
import { ADMIN_ID, staffHandlers } from "../test/staffApiMocks";
import { StaffManagement } from "./StaffManagement";

beforeEach(() =>
  useSessionStore.setState({
    accessToken: "tok",
    status: "authenticated",
    user: { id: ADMIN_ID, email: "ana@clinica.test", fullName: "Ana Admin", role: "ADMIN",
      organizationId: 1, organizationName: "Clínica", organizationSlug: "clinica" },
  }),
);

function renderAt(url = "/settings/staff") {
  return renderWithProviders(<><StaffManagement /><Toaster /></>, { initialEntries: [url] });
}

const personal = () => screen.findByRole("list", { name: "Personal de la clínica" });
const invitationsList = () => screen.findByRole("list", { name: "Invitaciones de la clínica" });

describe("StaffManagement — pestaña Personal", () => {
  it("muestra a cada miembro con su rol y estado, incluido el administrador", async () => {
    server.use(...staffHandlers().handlers);
    renderAt();
    const list = await personal();
    expect(within(list).getByText("María Ruiz")).toBeInTheDocument();
    expect(within(list).getByText("Ana Admin")).toBeInTheDocument();
    expect(within(list).getByText("(tú)")).toBeInTheDocument();
    expect(within(list).getAllByText("Inactivo")).toHaveLength(1);
  });

  it("busca por nombre sin distinguir tildes ni mayúsculas", async () => {
    server.use(...staffHandlers().handlers);
    const user = userEvent.setup();
    renderAt();
    await personal();
    await user.type(screen.getByRole("searchbox", { name: "Buscar en el personal" }), "MARIA");
    const list = await personal();
    expect(within(list).getByText("María Ruiz")).toBeInTheDocument();
    // La fila filtrada sale con su animación de salida (AnimatePresence): se espera a que termine.
    await waitFor(() => expect(within(list).queryByText("Luis Pérez")).not.toBeInTheDocument());
  });

  it("una búsqueda sin resultados lo indica y permite limpiarla", async () => {
    server.use(...staffHandlers().handlers);
    const user = userEvent.setup();
    renderAt();
    await personal();
    await user.type(screen.getByRole("searchbox", { name: "Buscar en el personal" }), "zzz");
    expect(await screen.findByText("Nadie coincide con tu búsqueda")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Limpiar búsqueda" }));
    expect(within(await personal()).getByText("María Ruiz")).toBeInTheDocument();
  });

  it("no ofrece acciones sobre la propia fila ni sobre otro administrador", async () => {
    server.use(...staffHandlers().handlers);
    renderAt();
    await personal();
    expect(screen.queryByRole("button", { name: /Ana Admin/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Otra Admin/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Desactivar a María Ruiz" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reactivar a Luis Pérez" })).toBeInTheDocument();
  });

  it("desactivar pide confirmación y, al aceptar, envía el cambio", async () => {
    const { handlers, calls } = staffHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderAt();
    await personal();
    await user.click(screen.getByRole("button", { name: "Desactivar a María Ruiz" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/No podrá iniciar sesión/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Desactivar" }));
    await waitFor(() => expect(calls.statusChanges).toEqual([{ id: "2", body: { status: "INACTIVE" } }]));
  });

  it("cancelar la confirmación no cambia nada", async () => {
    const { handlers, calls } = staffHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderAt();
    await personal();
    await user.click(screen.getByRole("button", { name: "Desactivar a María Ruiz" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(calls.statusChanges).toEqual([]);
  });
});

// Scenarios de staff: Capacidad clínica en el listado del personal · Retirar y restituir la capacidad clínica.
describe("StaffManagement — capacidad clínica", () => {
  it("muestra la capacidad de cada miembro, con su COP cuando lo declaró", async () => {
    server.use(...staffHandlers().handlers);
    renderAt();
    const list = await personal();
    expect(within(list).getByText(/^Habilitado hasta 30 jun\.? 2099$/)).toBeInTheDocument();
    expect(within(list).getByText("COP 22222")).toBeInTheDocument();
    expect(within(list).getByText(/^Habilitación vencida el 20 oct\.? 2026$/)).toBeInTheDocument();
    expect(within(list).getByText("Sin credencial")).toBeInTheDocument();
    expect(within(list).getByText("Retirada por la clínica")).toBeInTheDocument();
    expect(within(list).getByText("Cuenta inactiva")).toBeInTheDocument();
  });

  it("no ofrece retirar la capacidad propia, la de otro administrador ni la de un miembro inactivo", async () => {
    server.use(...staffHandlers().handlers);
    renderAt();
    await personal();
    expect(screen.queryByRole("button", { name: /capacidad clínica de Ana Admin/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /capacidad clínica de Otra Admin/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /capacidad clínica de Luis Pérez/ })).not.toBeInTheDocument();
  });

  it("retirar pide confirmación y, al aceptar, envía el retiro", async () => {
    const { handlers, calls } = staffHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderAt();
    await personal();
    await user.click(screen.getByRole("button", { name: "Retirar la capacidad clínica de María Ruiz" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/Lo que ya firmó sigue válido/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Retirar" }));
    await waitFor(() => expect(calls.capacityChanges).toEqual([{ id: "2", body: { revoked: true } }]));
  });

  it("cancelar el retiro no cambia nada", async () => {
    const { handlers, calls } = staffHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderAt();
    await personal();
    await user.click(screen.getByRole("button", { name: "Retirar la capacidad clínica de María Ruiz" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(calls.capacityChanges).toEqual([]);
  });

  it("a quien se la retiraron se le ofrece restituirla", async () => {
    const { handlers, calls } = staffHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderAt();
    await personal();
    await user.click(screen.getByRole("button", { name: "Restituir la capacidad clínica de Rosa Vega" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/si su credencial está vigente/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Restituir" }));
    await waitFor(() => expect(calls.capacityChanges).toEqual([{ id: "5", body: { revoked: false } }]));
  });
});

describe("StaffManagement — pestaña Invitaciones", () => {
  it("se abre desde la URL y muestra cada invitación con su estado", async () => {
    server.use(...staffHandlers().handlers);
    renderAt("/settings/staff?tab=invitaciones");
    const list = await invitationsList();
    expect(within(list).getByText("pendiente@x.test")).toBeInTheDocument();
    expect(within(list).getByText("Vencida")).toBeInTheDocument();
    expect(within(list).getAllByText("Aceptada")).toHaveLength(2);
  });

  it("filtra por estado y muestra cuántas hay en cada uno", async () => {
    server.use(...staffHandlers().handlers);
    const user = userEvent.setup();
    renderAt("/settings/staff?tab=invitaciones");
    await invitationsList();
    expect(screen.getByRole("button", { name: /Todas \(4\)/ })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: /Vencidas \(1\)/ }));
    const list = await invitationsList();
    expect(within(list).getByText("vencida@x.test")).toBeInTheDocument();
    await waitFor(() => expect(within(list).queryByText("pendiente@x.test")).not.toBeInTheDocument());
  });

  it("un filtro sin resultados no se confunde con una clínica sin invitaciones", async () => {
    server.use(...staffHandlers().handlers);
    const user = userEvent.setup();
    renderAt("/settings/staff?tab=invitaciones");
    await invitationsList();
    await user.click(screen.getByRole("button", { name: /Revocadas \(0\)/ }));
    expect(await screen.findByText("No hay invitaciones con este estado")).toBeInTheDocument();
    expect(screen.queryByText("Aún no invitaste a nadie")).not.toBeInTheDocument();
  });

  it("sin invitaciones muestra el estado vacío que invita a emitir la primera", async () => {
    server.use(...staffHandlers({ invitations: [] }).handlers);
    renderAt("/settings/staff?tab=invitaciones");
    expect(await screen.findByText("Aún no invitaste a nadie")).toBeInTheDocument();
  });

  it("solo las gestionables ofrecen reenviar y revocar", async () => {
    server.use(...staffHandlers().handlers);
    renderAt("/settings/staff?tab=invitaciones");
    await invitationsList();
    expect(screen.getByRole("button", { name: "Reenviar la invitación a pendiente@x.test" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reenviar la invitación a vencida@x.test" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /a aceptada@x.test/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /a admin@x.test/ })).not.toBeInTheDocument();
  });

  it("reenviar llama a la API sin pedir confirmación", async () => {
    const { handlers, calls } = staffHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderAt("/settings/staff?tab=invitaciones");
    await invitationsList();
    await user.click(screen.getByRole("button", { name: "Reenviar la invitación a vencida@x.test" }));
    await waitFor(() => expect(calls.resends).toEqual(["11"]));
    expect(await screen.findByText(/El enlace anterior ya no sirve/)).toBeInTheDocument();
  });

  it("revocar pide confirmación; cancelar no revoca y aceptar sí", async () => {
    const { handlers, calls } = staffHandlers();
    server.use(...handlers);
    const user = userEvent.setup();
    renderAt("/settings/staff?tab=invitaciones");
    await invitationsList();
    await user.click(screen.getByRole("button", { name: "Revocar la invitación a pendiente@x.test" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Cancelar" }));
    expect(calls.revokes).toEqual([]);

    await user.click(screen.getByRole("button", { name: "Revocar la invitación a pendiente@x.test" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Revocar" }));
    await waitFor(() => expect(calls.revokes).toEqual(["10"]));
  });
});

describe("StaffManagement — invitar", () => {
  it("un correo inválido muestra el error y no envía la petición", async () => {
    const posted: unknown[] = [];
    const { handlers } = staffHandlers();
    server.use(...handlers);
    server.events.on("request:start", ({ request }) => {
      if (request.method === "POST" && request.url.endsWith("/staff/invitations")) posted.push(request.url);
    });
    const user = userEvent.setup();
    renderAt();
    await personal();
    await user.click(screen.getByRole("button", { name: "Invitar" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Correo electrónico/), "no-es-correo");
    await user.click(within(dialog).getByRole("button", { name: "Enviar invitación" }));
    expect(await within(dialog).findByText("Correo electrónico no válido")).toBeInTheDocument();
    expect(posted).toEqual([]);
    server.events.removeAllListeners();
  });

  it("un conflicto del servidor se muestra junto al campo y el diálogo sigue abierto", async () => {
    server.use(...staffHandlers({ inviteStatus: 409 }).handlers);
    const user = userEvent.setup();
    renderAt();
    await personal();
    await user.click(screen.getByRole("button", { name: "Invitar" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Correo electrónico/), "pendiente@x.test");
    await user.click(within(dialog).getByRole("button", { name: "Enviar invitación" }));
    expect(await within(dialog).findByText(/pendiente o vencida/)).toBeInTheDocument();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
