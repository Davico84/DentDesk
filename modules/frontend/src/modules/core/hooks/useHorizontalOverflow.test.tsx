import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { useRef } from "react";
import { useHorizontalOverflow } from "./useHorizontalOverflow";

/** ResizeObserver de prueba: guarda el callback para dispararlo a mano. */
let resize: (() => void) | null = null;
const OriginalResizeObserver = globalThis.ResizeObserver;

beforeEach(() => {
  globalThis.ResizeObserver = class {
    constructor(cb: () => void) {
      resize = cb;
    }
    observe() {}
    unobserve() {}
    disconnect() {}
  } as unknown as typeof ResizeObserver;
});

afterEach(() => {
  globalThis.ResizeObserver = OriginalResizeObserver;
  resize = null;
});

function Probe() {
  const ref = useRef<HTMLDivElement>(null);
  const { left, right } = useHorizontalOverflow(ref);
  return (
    <div ref={ref} data-testid="box" data-left={left} data-right={right}>
      <div />
    </div>
  );
}

/** jsdom no maqueta: se fijan a mano los anchos que mediría el navegador. */
function setMetrics(el: HTMLElement, { scrollWidth, clientWidth, scrollLeft }: Record<string, number>) {
  Object.defineProperty(el, "scrollWidth", { configurable: true, value: scrollWidth });
  Object.defineProperty(el, "clientWidth", { configurable: true, value: clientWidth });
  el.scrollLeft = scrollLeft;
}

describe("useHorizontalOverflow", () => {
  it("si el contenido entra, no sobresale por ningún lado", () => {
    render(<Probe />);
    const box = screen.getByTestId("box");
    setMetrics(box, { scrollWidth: 300, clientWidth: 300, scrollLeft: 0 });
    act(() => resize!());
    expect(box).toHaveAttribute("data-left", "false");
    expect(box).toHaveAttribute("data-right", "false");
  });

  it("al inicio de un contenido más ancho, sobresale solo a la derecha", () => {
    render(<Probe />);
    const box = screen.getByTestId("box");
    setMetrics(box, { scrollWidth: 600, clientWidth: 300, scrollLeft: 0 });
    act(() => resize!());
    expect(box).toHaveAttribute("data-left", "false");
    expect(box).toHaveAttribute("data-right", "true");
  });

  it("al deslizar hasta el final, sobresale solo a la izquierda", () => {
    render(<Probe />);
    const box = screen.getByTestId("box");
    setMetrics(box, { scrollWidth: 600, clientWidth: 300, scrollLeft: 0 });
    act(() => resize!());
    setMetrics(box, { scrollWidth: 600, clientWidth: 300, scrollLeft: 300 });
    act(() => {
      box.dispatchEvent(new Event("scroll"));
    });
    expect(box).toHaveAttribute("data-left", "true");
    expect(box).toHaveAttribute("data-right", "false");
  });

  it("un subpíxel de diferencia no cuenta como contenido oculto", () => {
    render(<Probe />);
    const box = screen.getByTestId("box");
    setMetrics(box, { scrollWidth: 301, clientWidth: 300, scrollLeft: 0 });
    act(() => resize!());
    expect(box).toHaveAttribute("data-right", "false");
  });
});
