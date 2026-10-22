import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { DentalChartFindingResponse, DentalFindingCatalogEntryResponse } from "@/modules/core/services/generated/model";
import type { Placement } from "@/modules/dental-chart/guidance/placement";
import { Odontogram } from "./Odontogram";

/**
 * Scenarios de <i>Elegir la localización en pantallas táctiles</i> (update-dental-chart-touch). El toque se simula con
 * la secuencia de punteros del navegador (`pointerdown` "touch", `pointerup`, `click`; sin los eventos de mouse
 * de compatibilidad, que el código no escucha): determinista en jsdom, que no tiene layout para el SVG (design D1).
 */
const CATALOG: DentalFindingCatalogEntryResponse[] = [
  { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false },
];
const CARIES_16: DentalChartFindingResponse = {
  id: 1,
  blockId: 1,
  type: "LESION_CARIES",
  subtype: "CD",
  location: { locationKind: "SURFACE", toothA: 16, surface: "OCLUSAL" },
};

function Harness({
  initial = null,
  interactive = true,
  findings = [],
  onSelect,
}: {
  initial?: Placement | null;
  interactive?: boolean;
  findings?: DentalChartFindingResponse[];
  onSelect: (l: unknown) => void;
}) {
  const [placement, setPlacement] = useState<Placement | null>(initial);
  return (
    <>
      <Odontogram
        findings={findings}
        catalog={CATALOG}
        interactive={interactive}
        placement={placement}
        onPlacementChange={setPlacement}
        onSelectLocation={onSelect}
      />
      <output data-testid="placement">{JSON.stringify(placement)}</output>
    </>
  );
}

/** Un toque con el dedo: pointerdown y pointerup "touch", y su click. */
function tap(el: Element) {
  fireEvent.pointerDown(el, { pointerType: "touch" });
  fireEvent.pointerUp(el, { pointerType: "touch" });
  fireEvent.click(el);
}

const face = (tooth: number, surface: string) => screen.getByRole("button", { name: new RegExp(`^Pieza ${tooth}, superficie ${surface}`) });
const magnifier = () => screen.findByRole("dialog", { name: /^Pieza \d+$/ });

describe("Odontograma con el dedo (update-dental-chart-touch)", () => {
  it("tocar una pieza la amplía con sus 5 caras, la pieza completa y los espacios con sus vecinas, sin registrar nada", async () => {
    const onSelect = vi.fn();
    render(<Harness onSelect={onSelect} />);

    tap(face(16, "oclusal"));

    const panel = await magnifier();
    expect(panel).toHaveAccessibleName("Pieza 16");
    for (const name of ["vestibular", "palatino", "mesial", "distal", "oclusal"]) {
      expect(within(panel).getByRole("button", { name: new RegExp(`^Cara ${name} de la 16`) })).toBeInTheDocument();
    }
    expect(within(panel).getByRole("button", { name: "Toda la pieza 16" })).toBeEnabled();
    expect(within(panel).getByRole("button", { name: "Entre la 16 y la 15" })).toBeInTheDocument();
    expect(within(panel).getByRole("button", { name: "Entre la 16 y la 17" })).toBeInTheDocument();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("elegir la cara oclusal cierra el panel y abre su registro", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Harness onSelect={onSelect} />);

    tap(face(16, "oclusal"));
    await user.click(within(await magnifier()).getByRole("button", { name: /^Cara oclusal de la 16/ }));

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "SURFACE", toothA: 16, surface: "OCLUSAL" });
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  });

  it("elegir el espacio con la 15 abre el registro entre la 16 y la 15", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Harness onSelect={onSelect} />);

    tap(face(16, "mesial"));
    await user.click(within(await magnifier()).getByRole("button", { name: "Entre la 16 y la 15" }));

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "BETWEEN_TEETH", toothA: 16, toothB: 15 });
  });

  it("una pieza del extremo de la fila ofrece un solo espacio", async () => {
    render(<Harness onSelect={vi.fn()} />);

    tap(face(18, "oclusal"));

    const panel = await magnifier();
    expect(within(panel).getAllByRole("button", { name: /^Entre la 18 y la/ })).toHaveLength(1);
    expect(within(panel).getByRole("button", { name: "Entre la 18 y la 17" })).toBeInTheDocument();
  });

  it("la pieza ampliada lista lo ya registrado y marca la cara que tiene hallazgos", async () => {
    render(<Harness findings={[CARIES_16]} onSelect={vi.fn()} />);

    tap(screen.getByRole("button", { name: /^Pieza 16, superficie oclusal: 1 hallazgo/ }));

    const panel = await magnifier();
    expect(within(panel).getByRole("button", { name: "Cara oclusal de la 16: 1 hallazgo(s)" })).toBeInTheDocument();
    expect(within(panel).getByRole("list", { name: "Hallazgos ya registrados en la pieza" })).toHaveTextContent(
      "Pieza 16, cara oclusal: Lesión de caries",
    );
  });

  it("cerrar sin elegir no registra nada", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Harness onSelect={onSelect} />);

    tap(face(16, "oclusal"));
    await magnifier();
    await user.keyboard("{Escape}");

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(onSelect).not.toHaveBeenCalled();
  });

  it("con el mouse la cara va directo, sin pieza ampliada", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Harness onSelect={onSelect} />);

    await user.click(face(16, "oclusal"));

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "SURFACE", toothA: 16, surface: "OCLUSAL" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("con el teclado va directo aunque antes se haya tocado con el dedo", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Harness onSelect={onSelect} />);

    // Un toque que no llegó a click (el dedo se fue): el tipo queda guardado en la pieza…
    fireEvent.pointerDown(face(16, "oclusal"), { pointerType: "touch" });
    // …y luego Enter sobre una cara de esa misma pieza.
    face(16, "mesial").focus();
    await user.keyboard("{Enter}");

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "SURFACE", toothA: 16, surface: "MESIAL" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("un click sin pointerdown previo no hereda el toque anterior y va directo", async () => {
    const onSelect = vi.fn();
    const user = userEvent.setup();
    render(<Harness onSelect={onSelect} />);

    // Un toque completo abre el panel y consume el tipo guardado…
    tap(face(16, "oclusal"));
    await magnifier();
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    // …y un click suelto después (sintético, de accesibilidad) va directo a la cara.
    fireEvent.click(face(16, "mesial"));

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "SURFACE", toothA: 16, surface: "MESIAL" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("ubicando un hallazgo de superficie, la pieza ampliada ofrece solo sus caras", async () => {
    render(<Harness initial={{ scope: "SURFACE", type: "LESION_CARIES" }} onSelect={vi.fn()} />);

    tap(face(16, "oclusal"));

    const panel = await magnifier();
    expect(within(panel).getByRole("button", { name: /^Cara oclusal de la 16/ })).not.toHaveAttribute("aria-disabled");
    expect(within(panel).getByRole("button", { name: "Toda la pieza 16" })).toBeDisabled();
    expect(within(panel).getByRole("button", { name: "Entre la 16 y la 15" })).toBeDisabled();
  });

  it("ubicando un hallazgo de pieza completa, el toque va directo a la pieza", () => {
    const onSelect = vi.fn();
    render(<Harness initial={{ scope: "TOOTH", type: "CORONA" }} onSelect={onSelect} />);

    tap(face(16, "oclusal"));

    expect(onSelect).toHaveBeenCalledWith({ locationKind: "TOOTH", toothA: 16 });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("ubicando un hallazgo entre piezas, la pieza responde y ofrece solo sus espacios", async () => {
    render(<Harness initial={{ scope: "BETWEEN_TEETH", type: "DIASTEMA" }} onSelect={vi.fn()} />);

    tap(face(16, "oclusal"));

    const panel = await magnifier();
    expect(within(panel).getByRole("button", { name: /^Cara oclusal de la 16/ })).toHaveAttribute("aria-disabled", "true");
    expect(within(panel).getByRole("button", { name: "Toda la pieza 16" })).toBeDisabled();
    expect(within(panel).getByRole("button", { name: "Entre la 16 y la 15" })).toBeEnabled();
  });

  it("en modo rango el toque ancla la pieza, sin pieza ampliada", () => {
    render(<Harness initial={{ scope: "TOOTH_RANGE" }} onSelect={vi.fn()} />);

    tap(face(14, "oclusal"));

    expect(JSON.parse(screen.getByTestId("placement").textContent ?? "null")).toEqual({ scope: "TOOTH_RANGE", rangeStart: 14 });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("en un odontograma sellado el toque no amplía ni registra", () => {
    const onSelect = vi.fn();
    render(<Harness interactive={false} onSelect={onSelect} />);

    tap(face(16, "oclusal"));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(onSelect).not.toHaveBeenCalled();
  });
});
