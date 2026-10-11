import { describe, expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { SupportCard } from "./SupportCard";
import { SupportDisclosure } from "./SupportDisclosure";

// Scenarios de `project-foundation` › Contacto de soporte en las pantallas de acceso.

const CONTACT = {
  name: "Ana Torres",
  role: "Soporte Demo",
  hours: "de 9:00 a 20:00",
  whatsapp: "51900000000",
  email: "soporte@demo.test",
  product: "Demo",
};

describe("SupportCard", () => {
  it("muestra quién atiende, su horario y las iniciales", () => {
    render(<SupportCard {...CONTACT} />);
    const card = screen.getByRole("region", { name: "Soporte" });
    expect(card).toHaveTextContent("Ana Torres · Soporte Demo");
    expect(card).toHaveTextContent("Respondo de 9:00 a 20:00");
    expect(card).toHaveTextContent("AT");
  });

  it("WhatsApp abre en otra pestaña con el primer mensaje y lo avisa en su nombre", () => {
    render(<SupportCard {...CONTACT} />);
    const wa = screen.getByRole("link", { name: /WhatsApp \(se abre en otra pestaña\)/ });
    expect(wa).toHaveAttribute("href", "https://wa.me/51900000000?text=Hola%2C%20necesito%20ayuda%20con%20Demo");
    expect(wa).toHaveAttribute("target", "_blank");
    expect(wa).toHaveAttribute("rel", "noopener noreferrer");
  });

  it("el correo es un enlace mailto con la dirección en su nombre accesible", () => {
    render(<SupportCard {...CONTACT} />);
    expect(screen.getByRole("link", { name: /Correo soporte@demo.test/ })).toHaveAttribute(
      "href",
      "mailto:soporte@demo.test",
    );
  });

  it("acepta otra frase de entrada", () => {
    render(<SupportCard {...CONTACT} lead="¿Sigue sin entrar? Escríbeme" />);
    expect(screen.getByText("¿Sigue sin entrar? Escríbeme")).toBeInTheDocument();
  });
});

describe("SupportDisclosure", () => {
  it("empieza cerrado; al pulsar despliega la tarjeta y al volver a pulsar la oculta", async () => {
    const user = userEvent.setup();
    render(<SupportDisclosure {...CONTACT} />);
    const toggle = screen.getByRole("button", { name: "¿Problemas para entrar? Habla con soporte" });
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    expect(screen.queryByRole("region", { name: "Soporte" })).not.toBeInTheDocument();

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "true");
    const card = screen.getByRole("region", { name: "Soporte" });
    expect(toggle).toHaveAttribute("aria-controls", card.id);

    await user.click(toggle);
    expect(toggle).toHaveAttribute("aria-expanded", "false");
    await vi.waitFor(() => expect(screen.queryByRole("region", { name: "Soporte" })).not.toBeInTheDocument());
  });
});
