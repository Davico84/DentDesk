import { expect, test } from "@playwright/test";

// Smoke E2E: sin backend real (docs/testing.md §4). El bootstrap de sesión hace un
// refresh best-effort y degrada a "no autenticado" si no hay API — por eso este flujo
// es válido contra el dev server solo, sin Testcontainers ni seed de datos.

test("un visitante sin sesión que entra a una ruta privada termina en el login", async ({ page }) => {
  await page.goto("/patients");
  await expect(page).toHaveURL(/\/auth\/login/);
  await expect(page.getByRole("heading", { name: "Iniciar sesión" })).toBeVisible();
});

test("el login valida el correo antes de tocar el servidor", async ({ page }) => {
  await page.goto("/auth/login");

  await page.getByLabel("Correo electrónico").fill("no-es-un-correo");
  // getByLabel("Contraseña") es ambiguo: el botón de mostrar/ocultar también se
  // llama "Contraseña" por su aria-label — getByRole("textbox") lo desambigua.
  await page.getByRole("textbox", { name: "Contraseña" }).fill("cualquier-cosa");
  await page.getByRole("button", { name: "Iniciar sesión" }).click();

  await expect(page.getByText("Correo electrónico no válido")).toBeVisible();
  // Sigue en /auth/login: el 400/401 nunca llegó a viajar.
  await expect(page).toHaveURL(/\/auth\/login/);
});

test("el login explica que el alta es por invitación", async ({ page }) => {
  await page.goto("/auth/login");
  await expect(page.getByText(/pide una invitación al administrador/i)).toBeVisible();
  await expect(page.getByRole("link", { name: "Crear cuenta" })).toHaveCount(0);
});
