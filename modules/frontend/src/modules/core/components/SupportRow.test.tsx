import { describe, expect, it } from "vitest";
import { act, render, screen } from "@testing-library/react";
import { SupportRow } from "./SupportRow";

// Scenarios de `project-foundation` › Contacto de soporte en las pantallas de acceso.

const CONTACT = { whatsapp: "51900000000", email: "soporte@demo.test", product: "Demo" };

describe("SupportRow", () => {
  it("muestra la pregunta y dos botones de solo ícono con nombre accesible y sin title", () => {
    render(<SupportRow {...CONTACT} />);
    const row = screen.getByRole("region", { name: "Soporte" });
    expect(row).toHaveTextContent("¿Problemas para entrar?");
    for (const link of screen.getAllByRole("link")) expect(link).not.toHaveAttribute("title");
  });

  it("WhatsApp abre en otra pestaña con el primer mensaje y lo avisa en su nombre", () => {
    render(<SupportRow {...CONTACT} />);
    const wa = screen.getByRole("link", { name: "Escribir a soporte por WhatsApp (se abre en otra pestaña)" });
    expect(wa).toHaveAttribute("href", "https://wa.me/51900000000?text=Hola%2C%20necesito%20ayuda%20con%20Demo");
    expect(wa).toHaveAttribute("target", "_blank");
    expect(wa).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("el correo es un enlace mailto con la dirección en su nombre accesible", () => {
    render(<SupportRow {...CONTACT} />);
    expect(screen.getByRole("link", { name: "Escribir a soporte por correo, soporte@demo.test" })).toHaveAttribute(
      "href",
      "mailto:soporte@demo.test",
    );
  });

  it("al enfocar un botón, un tooltip muestra su nombre", async () => {
    render(<SupportRow {...CONTACT} />);
    act(() => screen.getByRole("link", { name: /WhatsApp/ }).focus());
    expect(await screen.findByRole("tooltip")).toHaveTextContent("WhatsApp");
  });

  it("acepta otra pregunta", () => {
    render(<SupportRow {...CONTACT} lead="¿Sigue sin entrar?" />);
    expect(screen.getByRole("region", { name: "Soporte" })).toHaveTextContent("¿Sigue sin entrar?");
  });
});
