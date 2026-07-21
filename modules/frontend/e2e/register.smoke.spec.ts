import { expect, test } from "@playwright/test";

// Smoke E2E: sin backend real (docs/testing.md §4), simétrico a auth.smoke.spec.ts para
// el flujo de registro — solo lo que degrada con gracia sin API (validación cliente,
// navegación). El registro real (crea organización + identidad) necesita backend, no
// entra acá.

test("el registro valida que las contraseñas coincidan antes de tocar el servidor", async ({ page }) => {
  await page.goto("/auth/register");

  await page.getByLabel("Nombre de la clínica").fill("Clínica Dental Sonrisas");
  await page.getByLabel("Nombre completo").fill("Ana Test");
  await page.getByLabel("Correo electrónico").fill("ana@clinica.test");
  // getByLabel("Contraseña") es ambiguo acá (matchea también "Confirmar contraseña" y el
  // botón de mostrar/ocultar) — getByRole("textbox", { exact: true }) desambigua.
  await page.getByRole("textbox", { name: "Contraseña", exact: true }).fill("password123");
  await page.getByRole("textbox", { name: "Confirmar contraseña", exact: true }).fill("otra-clave");
  await page.getByRole("button", { name: "Crear cuenta" }).click();

  await expect(page.getByText("Las contraseñas no coinciden")).toBeVisible();
  // Sigue en /auth/register: la petición nunca viajó.
  await expect(page).toHaveURL(/\/auth\/register/);
});

test("el registro enlaza al login", async ({ page }) => {
  await page.goto("/auth/register");
  await page.getByRole("link", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/auth\/login/);
});
