import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import { SignedByLine } from "./SignedByLine";

// La misma línea la usan la Historia, el odontograma y la nota selladas (spec clinical-history y dental-chart).
describe("SignedByLine", () => {
  it("muestra al firmante con su COP", () => {
    render(<SignedByLine signer={{ fullName: "Dra. Ana Ruiz", copNumber: "12345", licensedUntil: "2099-12-31" }} />);
    expect(screen.getByText("Firmado por Dra. Ana Ruiz · COP 12345")).toBeInTheDocument();
  });

  it("lo sellado antes del registro de credenciales lo dice, sin inventar un firmante", () => {
    render(<SignedByLine signer={null} signedBeforeCredentials />);
    expect(screen.getByText("Firmado antes del registro de credenciales")).toBeInTheDocument();
  });

  it("sin firma no muestra nada", () => {
    const { container } = render(<SignedByLine signer={null} signedBeforeCredentials={false} />);
    expect(container).toBeEmptyDOMElement();
  });
});
