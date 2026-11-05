import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

// Scenario "El enlace no queda en el historial" (update-authentication-password-recovery D7): la página del
// enlace no manda su dirección, con el token, como referencia a otros sitios. La política vive en el index.html
// que sirve toda la app.
const indexHtml = readFileSync(resolve(__dirname, "../../../../index.html"), "utf-8");

describe("index.html", () => {
  it("declara la política no-referrer", () => {
    const document = new DOMParser().parseFromString(indexHtml, "text/html");
    expect(document.querySelector('meta[name="referrer"]')?.getAttribute("content")).toBe("no-referrer");
  });
});
