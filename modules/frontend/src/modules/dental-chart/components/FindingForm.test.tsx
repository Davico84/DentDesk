import { describe, it, expect } from "vitest";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { renderWithProviders } from "@/test/utils";
import { server } from "@/test/mocks/server";
import type { DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import { FindingForm } from "./FindingForm";

const CATALOG: DentalFindingCatalogEntryResponse[] = [
  { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false },
  { type: "MOVILIDAD_PATOLOGICA", subtype: "", acronym: "M", scope: "TOOTH", colorBehavior: "SIEMPRE_ROJO", requiresGrade: true, requiresSpecification: true },
];

/**
 * Regresión: `location.locationKind` no tenía ningún campo que lo escribiera — quedaba "" para
 * siempre y `buildFindingSchema` lo rechazaba en silencio (el error caía en un campo sin UI), así
 * que "Agregar hallazgo" no hacía nada. Cubre exactamente el flujo que lo expuso: elegir tipo +
 * pieza desde el formulario, sin pasar antes por el click en el odontograma.
 */
describe("FindingForm", () => {
  it("envía location.locationKind derivado del alcance del tipo elegido, sin tocar el odontograma", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/dental-chart/findings", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ finding: { id: 1 }, denticionWarning: false }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<FindingForm patientId={1} catalog={CATALOG} />);

    await user.selectOptions(screen.getByLabelText(/tipo de hallazgo/i), "LESION_CARIES");
    await user.selectOptions(screen.getByLabelText(/subtipo/i), "CD");
    await user.type(screen.getByLabelText(/pieza \(fdi\)/i), "36");
    await user.selectOptions(screen.getByLabelText(/superficie/i), "OCLUSAL");
    await user.click(screen.getByRole("button", { name: /agregar hallazgo/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect(received).toMatchObject({
      type: "LESION_CARIES",
      subtype: "CD",
      location: { locationKind: "SURFACE", toothA: 36, surface: "OCLUSAL" },
    });
  });

  it("un tipo con grado exigido (movilidad) lo pide y lo manda en el body", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/dental-chart/findings", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ finding: { id: 2 }, denticionWarning: false }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<FindingForm patientId={1} catalog={CATALOG} />);

    await user.selectOptions(screen.getByLabelText(/tipo de hallazgo/i), "MOVILIDAD_PATOLOGICA");
    await user.type(screen.getByLabelText(/grado de movilidad/i), "2");
    await user.type(screen.getByLabelText(/pieza \(fdi\)/i), "41");
    await user.click(screen.getByRole("button", { name: /agregar hallazgo/i }));

    await waitFor(() => expect(received).not.toBeNull());
    expect(received).toMatchObject({
      type: "MOVILIDAD_PATOLOGICA",
      grade: 2,
      location: { locationKind: "TOOTH", toothA: 41 },
    });
  });

  /**
   * Regresión: cambiar de un tipo SURFACE a uno TOOTH dejaba `location.surface` con el valor
   * viejo — el servidor lo rechazaba ("no aplica a esta forma de localización") aunque ya no
   * hubiera ningún campo de superficie visible en el formulario para corregirlo.
   */
  it("cambiar de un tipo de superficie a uno de pieza no arrastra el campo de superficie", async () => {
    let received: unknown = null;
    server.use(
      http.post("*/patients/1/dental-chart/findings", async ({ request }) => {
        received = await request.json();
        return HttpResponse.json({ finding: { id: 3 }, denticionWarning: false }, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(<FindingForm patientId={1} catalog={CATALOG} />);

    await user.selectOptions(screen.getByLabelText(/tipo de hallazgo/i), "LESION_CARIES");
    await user.selectOptions(screen.getByLabelText(/subtipo/i), "CD");
    await user.type(screen.getByLabelText(/pieza \(fdi\)/i), "36");
    await user.selectOptions(screen.getByLabelText(/superficie/i), "OCLUSAL");

    await user.selectOptions(screen.getByLabelText(/tipo de hallazgo/i), "MOVILIDAD_PATOLOGICA");
    expect(screen.queryByLabelText(/superficie/i)).not.toBeInTheDocument();
    await user.type(screen.getByLabelText(/grado de movilidad/i), "1");
    await user.click(screen.getByRole("button", { name: /agregar hallazgo/i }));

    await waitFor(() => expect(received).not.toBeNull());
    const body = received as { location: Record<string, unknown> };
    expect(body.location.locationKind).toBe("TOOTH");
    expect(body.location.surface).toBeUndefined();
  });

  /**
   * El user pidió que el select de subtipo muestre el nombre completo entre paréntesis, no solo
   * la sigla (verificado contra el PDF oficial: "CD" por sí solo no dice qué es).
   */
  it("el subtipo se lista con la sigla y el nombre completo entre paréntesis", async () => {
    renderWithProviders(<FindingForm patientId={1} catalog={CATALOG} />);

    await userEvent.setup().selectOptions(screen.getByLabelText(/tipo de hallazgo/i), "LESION_CARIES");

    const option = screen.getByRole("option", { name: "CD (Lesión de caries a nivel de la dentina)" });
    expect(option).toBeInTheDocument();
  });
});
