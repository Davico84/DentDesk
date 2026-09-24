import { describe, it, expect } from "vitest";
import { screen, waitFor } from "@testing-library/react";
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
];

const EXISTING: DentalChartFindingResponse = {
  id: 7,
  blockId: 1,
  type: "LESION_CARIES",
  subtype: "CD",
  location: { locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" },
};

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
    // Solo tipos de alcance TOOTH — SURFACE (LESION_CARIES, SELLANTE) queda afuera.
    const typeSelect = screen.getByLabelText(/tipo de hallazgo/i) as HTMLSelectElement;
    const values = Array.from(typeSelect.options).map((o) => o.value);
    expect(values).toContain("MOVILIDAD_PATOLOGICA");
    expect(values).not.toContain("LESION_CARIES");
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
    expect(screen.getByLabelText(/tipo de hallazgo/i)).toHaveValue("LESION_CARIES");
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
    await user.selectOptions(screen.getByLabelText(/tipo de hallazgo/i), "SELLANTE");
    await user.selectOptions(screen.getByLabelText(/estado de conservación/i), "GOOD");
    await user.click(screen.getByRole("button", { name: /^agregar hallazgo$/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect(received).toMatchObject({
      type: "SELLANTE",
      condition: "GOOD",
      location: { locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" },
    });
  });
});
