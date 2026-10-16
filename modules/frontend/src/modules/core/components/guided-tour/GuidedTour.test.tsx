import { describe, expect, it, vi } from "vitest";
import { useState } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { GuidedTour } from "./GuidedTour";
import type { TourStep } from "./tourTypes";

const STEPS: TourStep[] = [
  { title: "Bienvenida", body: "Así se registra un hallazgo." },
  { title: "Toda la pieza", body: "Toca el número de la pieza.", anchor: () => document.querySelector("[data-guide='target']"), examples: ["Corona"] },
  { title: "Listo", body: "Ya sabes dónde tocar." },
];

function Harness({ onClose = vi.fn(), onTargetClick = vi.fn() }: { onClose?: (r: string) => void; onTargetClick?: () => void }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>Abrir guía</button>
      <button type="button" data-guide="target" onClick={onTargetClick}>16</button>
      <GuidedTour
        open={open}
        steps={STEPS}
        onClose={(reason) => {
          onClose(reason);
          setOpen(false);
        }}
      />
    </>
  );
}

async function openTour() {
  const user = userEvent.setup();
  await user.click(screen.getByRole("button", { name: "Abrir guía" }));
  return user;
}

describe("GuidedTour", () => {
  it("empieza en el primer paso y se recorre con los botones", async () => {
    render(<Harness />);
    const user = await openTour();
    expect(screen.getByRole("dialog", { name: "Bienvenida" })).toBeInTheDocument();
    expect(screen.getByText("Paso 1 de 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Anterior" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(screen.getByRole("dialog", { name: "Toda la pieza" })).toBeInTheDocument();
    expect(screen.getByText("Corona")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Anterior" }));
    expect(screen.getByRole("dialog", { name: "Bienvenida" })).toBeInTheDocument();
  });

  it("se recorre con las flechas y Esc la cierra", async () => {
    const onClose = vi.fn();
    render(<Harness onClose={onClose} />);
    const user = await openTour();
    await user.keyboard("{ArrowRight}");
    expect(screen.getByText("Paso 2 de 3")).toBeInTheDocument();
    await user.keyboard("{ArrowLeft}");
    expect(screen.getByText("Paso 1 de 3")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    expect(onClose).toHaveBeenCalledWith("closed");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("terminar en el último paso cierra con motivo 'done'", async () => {
    const onClose = vi.fn();
    render(<Harness onClose={onClose} />);
    const user = await openTour();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    await user.click(screen.getByRole("button", { name: "Terminar" }));
    expect(onClose).toHaveBeenCalledWith("done");
  });

  it("cerrar con la X antes del final cierra con motivo 'closed'", async () => {
    const onClose = vi.fn();
    render(<Harness onClose={onClose} />);
    const user = await openTour();
    await user.click(screen.getByRole("button", { name: "Cerrar la guía" }));
    expect(onClose).toHaveBeenCalledWith("closed");
  });

  it("anuncia el paso, el total y la explicación", async () => {
    render(<Harness />);
    const user = await openTour();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(screen.getByText("Paso 2 de 3. Toda la pieza. Toca el número de la pieza.")).toBeInTheDocument();
  });

  it("un paso cuyo ancla no existe se muestra igual, centrado", async () => {
    render(<Harness />);
    const user = await openTour();
    document.querySelector("[data-guide='target']")?.removeAttribute("data-guide");
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(screen.getByRole("dialog", { name: "Toda la pieza" })).toHaveStyle({ top: "50%" });
  });

  it("un click sobre la zona resaltada no llega al elemento de abajo", async () => {
    const onTargetClick = vi.fn();
    render(<Harness onTargetClick={onTargetClick} />);
    const user = await openTour();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    // La capa tapa toda la pantalla: el click cae en ella, no en la pieza.
    await user.click(screen.getByTestId("tour-shield"));
    expect(onTargetClick).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog", { name: "Toda la pieza" })).toBeInTheDocument();
  });

  it("al cerrar, el foco vuelve a quien abrió la guía", async () => {
    render(<Harness />);
    const user = await openTour();
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.getByRole("button", { name: "Abrir guía" })).toHaveFocus());
  });
});
