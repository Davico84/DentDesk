import { describe, it, expect, beforeEach } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { OrgLogo } from "./OrgLogo";
import { useBrandingStore } from "@/store/useBrandingStore";

beforeEach(() => useBrandingStore.getState().clear());

describe("OrgLogo", () => {
  it("muestra el logo del producto cuando la organización no tiene logo", () => {
    render(<OrgLogo />);
    expect(screen.getByRole("img", { name: "Dentdesk" })).toBeInTheDocument();
  });

  it("muestra el logo de la organización cuando está definido", () => {
    useBrandingStore.getState().setBranding("https://cdn.test/logo.png", false);
    render(<OrgLogo />);
    expect(screen.getByAltText("Logo de la organización")).toHaveAttribute(
      "src",
      "https://cdn.test/logo.png",
    );
  });

  it("cae al logo del producto si la imagen de la organización falla (onError)", () => {
    useBrandingStore.getState().setBranding("https://cdn.test/roto.png", false);
    render(<OrgLogo />);
    fireEvent.error(screen.getByAltText("Logo de la organización"));
    expect(screen.getByRole("img", { name: "Dentdesk" })).toBeInTheDocument();
  });
});
