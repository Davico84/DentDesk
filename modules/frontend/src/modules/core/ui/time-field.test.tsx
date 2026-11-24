import { afterEach, describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useForm } from "react-hook-form";
import { TimeField } from "./form-field";

function Harness() {
  const { register } = useForm<{ time: string }>({ defaultValues: { time: "" } });
  return <TimeField label="Hora" name="time" register={register} step={300} min="09:00" max="17:50" />;
}

describe("TimeField", () => {
  const original = HTMLInputElement.prototype.showPicker;
  afterEach(() => {
    HTMLInputElement.prototype.showPicker = original;
  });

  it("todo el campo abre el selector del sistema, no solo el ícono", async () => {
    const showPicker = vi.fn();
    HTMLInputElement.prototype.showPicker = showPicker;
    render(<Harness />);

    await userEvent.setup().click(screen.getByLabelText("Hora"));

    expect(showPicker).toHaveBeenCalledTimes(1);
  });

  it("acota el selector con paso y límites, y sin selector se sigue pudiendo escribir", async () => {
    HTMLInputElement.prototype.showPicker = () => {
      throw new DOMException("No permitido", "NotAllowedError");
    };
    render(<Harness />);
    const input = screen.getByLabelText("Hora");

    expect(input).toHaveAttribute("step", "300");
    expect(input).toHaveAttribute("min", "09:00");
    await userEvent.setup().click(input);
    expect(input).toBeEnabled();
  });
});
