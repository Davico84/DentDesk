import { expect, test } from "@playwright/test";

// Smoke E2E: sin backend real (docs/testing.md §4), simétrico a auth.smoke.spec.ts para
// el flujo de registro — solo lo que degrada con gracia sin API. El alta real (invitación,
// código del correo, registro) necesita backend: la cubren PlatformAdminIT y StaffInvitationIT.

test("sin enlace de invitación el registro no ofrece formulario", async ({ page }) => {
  await page.goto("/auth/register");

  await expect(page.getByText("Necesitas una invitación")).toBeVisible();
  await expect(page.getByRole("textbox")).toHaveCount(0);
});

test("el registro enlaza al login", async ({ page }) => {
  await page.goto("/auth/register");
  await page.getByRole("link", { name: "Iniciar sesión" }).click();
  await expect(page).toHaveURL(/\/auth\/login/);
});
