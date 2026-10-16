import { describe, it, expect } from "vitest";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { FindingDialog } from "./FindingDialog";

const CATALOG: DentalFindingCatalogEntryResponse[] = [
  { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false },
  { type: "SELLANTE", subtype: "", acronym: "S", scope: "SURFACE", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: false },
  { type: "MOVILIDAD_PATOLOGICA", subtype: "", acronym: "M", scope: "TOOTH", colorBehavior: "SIEMPRE_ROJO", requiresGrade: true, requiresSpecification: true },
  { type: "DIASTEMA", subtype: "", acronym: undefined, scope: "BETWEEN_TEETH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
  { type: "PROTESIS_PARCIAL_FIJA", subtype: "", acronym: undefined, scope: "TOOTH_RANGE", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: false },
];

const EXISTING: DentalChartFindingResponse = {
  id: 7,
  blockId: 1,
  type: "LESION_CARIES",
  subtype: "CD",
  location: { locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" },
};

/** El tipo se elige en el combobox con búsqueda (update-dental-chart-guidance D5), no en un `<select>`. */
async function pickType(user: ReturnType<typeof userEvent.setup>, label: string) {
  await user.click(screen.getByRole("combobox", { name: /tipo de hallazgo/i }));
  await user.click(screen.getByRole("option", { name: new RegExp(`^${label}`) }));
}

describe("FindingDialog", () => {
  it("sin hallazgos previos, abre directo el formulario (sin paso de lista)", async () => {
    renderWithProviders(
      <FindingDialog
        open
        onOpenChange={() => {}}
        patientId={1}
        catalog={CATALOG}
        location={{ locationKind: "TOOTH", toothA: 41 }}
        existingFindings={[]}
      />,
    );

    expect(await screen.findByRole("heading", { name: "Agregar hallazgo" })).toBeInTheDocument();
    // Primero los de alcance TOOTH; los de superficie (LESION_CARIES, SELLANTE) van aparte, en "En otras zonas".
    await userEvent.setup().click(screen.getByRole("combobox", { name: /tipo de hallazgo/i }));
    const here = screen.getByRole("group", { name: "Para esta zona" });
    expect(within(here).getByRole("option", { name: "Movilidad patológica" })).toBeInTheDocument();
    expect(within(here).queryByRole("option", { name: /Lesión de caries/ })).not.toBeInTheDocument();
    expect(within(screen.getByRole("group", { name: "En otras zonas" })).getByRole("option", { name: /Lesión de caries/ })).toBeInTheDocument();
  });

  it("con un hallazgo existente, primero muestra la lista con editar/eliminar", async () => {
    renderWithProviders(
      <FindingDialog
        open
        onOpenChange={() => {}}
        patientId={1}
        catalog={CATALOG}
        location={{ locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" }}
        existingFindings={[EXISTING]}
      />,
    );

    expect(await screen.findByText(/1 hallazgo\(s\) registrado\(s\)/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Editar" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Eliminar" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /agregar otro hallazgo/i })).toBeInTheDocument();
  });

  it("editar precarga el tipo/subtipo del hallazgo y envía PUT al confirmar", async () => {
    let received: unknown = null;
    server.use(
      http.put("*/patients/1/dental-chart/findings/7", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ finding: { id: 7 }, denticionWarning: false });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <FindingDialog
        open
        onOpenChange={() => {}}
        patientId={1}
        catalog={CATALOG}
        location={{ locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" }}
        existingFindings={[EXISTING]}
      />,
    );

    await user.click(await screen.findByRole("button", { name: "Editar" }));

    expect(await screen.findByRole("heading", { name: "Editar hallazgo" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: /tipo de hallazgo/i })).toHaveValue("Lesión de caries");
    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect(received).toMatchObject({
      type: "LESION_CARIES",
      subtype: "CD",
      location: { locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" },
    });
  });

  it("agregar otro, desde la lista, pide el tipo y manda POST con la localización ya fija", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/dental-chart/findings", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ finding: { id: 8 }, denticionWarning: false }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <FindingDialog
        open
        onOpenChange={() => {}}
        patientId={1}
        catalog={CATALOG}
        location={{ locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" }}
        existingFindings={[EXISTING]}
      />,
    );

    await user.click(await screen.findByRole("button", { name: /agregar otro hallazgo/i }));
    await pickType(user, "Sellante");
    await user.selectOptions(screen.getByLabelText(/estado de conservación/i), "GOOD");
    await user.click(screen.getByRole("button", { name: /^agregar hallazgo$/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect(received).toMatchObject({
      type: "SELLANTE",
      condition: "GOOD",
      location: { locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" },
    });
  });

  /**
   * 5.8b: entre-piezas y rango también abren el modal directo, con la localización fija — antes
   * eran los únicos dos alcances sin click propio en el gráfico.
   */
  it("entre-piezas: abre directo al formulario y manda ambos FDI fijos", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/dental-chart/findings", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ finding: { id: 9 }, denticionWarning: false }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <FindingDialog
        open
        onOpenChange={() => {}}
        patientId={1}
        catalog={CATALOG}
        location={{ locationKind: "BETWEEN_TEETH", toothA: 11, toothB: 21 }}
        existingFindings={[]}
      />,
    );

    expect(await screen.findByText("Entre las piezas 11 y 21")).toBeInTheDocument();
    await pickType(user, "Diastema");
    await user.click(screen.getByRole("button", { name: /^agregar hallazgo$/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect(received).toMatchObject({ type: "DIASTEMA", location: { locationKind: "BETWEEN_TEETH", toothA: 11, toothB: 21 } });
  });

  it("rango: abre directo al formulario y manda las piezas fijas", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/dental-chart/findings", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ finding: { id: 10 }, denticionWarning: false }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(
      <FindingDialog
        open
        onOpenChange={() => {}}
        patientId={1}
        catalog={CATALOG}
        location={{ locationKind: "TOOTH_RANGE", teeth: [14, 13, 12] }}
        existingFindings={[]}
      />,
    );

    expect(await screen.findByText("Rango: piezas 14, 13, 12")).toBeInTheDocument();
    await pickType(user, "Prótesis parcial fija");
    await user.selectOptions(screen.getByLabelText(/estado de conservación/i), "GOOD");
    await user.click(screen.getByRole("button", { name: /^agregar hallazgo$/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect(received).toMatchObject({ type: "PROTESIS_PARCIAL_FIJA", location: { locationKind: "TOOTH_RANGE", teeth: [14, 13, 12] } });
  });
});
