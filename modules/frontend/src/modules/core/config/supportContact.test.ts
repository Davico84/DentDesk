import { describe, expect, it } from "vitest";
import { supportContact, whatsappUrl } from "./supportContact";

describe("supportContact", () => {
  it("el WhatsApp tiene el formato de wa.me: solo dígitos, con código de país", () => {
    expect(supportContact.whatsapp).toMatch(/^\d{8,15}$/);
  });

  it("el correo tiene forma de correo", () => {
    expect(supportContact.email).toMatch(/^[^\s@]+@[^\s@]+\.[^\s@]+$/);
  });

  it("el enlace de WhatsApp lleva el primer mensaje codificado", () => {
    expect(whatsappUrl("51959396384", "Hola, necesito ayuda con Dentdesk")).toBe(
      "https://wa.me/51959396384?text=Hola%2C%20necesito%20ayuda%20con%20Dentdesk",
    );
  });
});
