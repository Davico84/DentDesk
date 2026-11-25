import { expect, test, type Page } from "@playwright/test";

// add-app-shell, scenarios "Sin desplazamiento horizontal" y "App centrada en una pantalla muy
// ancha". La API se simula con `page.route` (solo fetch/xhr): basta para pintar cada pantalla
// privada con datos realistas, sin backend ni seed.

const USER = {
  id: 7, email: "ana@clinica.test", fullName: "Ana Test", role: "ADMIN",
  organizationId: 1, organizationName: "Clínica Sonrisas", organizationSlug: "clinica-sonrisas",
};
const PATIENT = {
  id: 1, fullName: "Armando Lios", documentType: "DNI", document: "12345678", birthDate: "1980-02-20",
  minor: false, sex: "MALE", phone: "+51987654321", email: "armando@test.local", address: "Calle de la Plata 103",
  emergencyContactName: "Karen", emergencyContactPhone: "+51123123213", emergencyContactRelationship: "Esposa",
  allergies: ["Látex"], conditions: ["Hipertensión"], currentMedication: [], status: "ACTIVE",
  phoneSharedWithOtherPatients: false, anamnesisFrozen: true,
};
const HISTORY = {
  id: 1, patientId: 1, status: "COMPLETED", fullName: "Armando Lios", sex: "MALE", birthDate: "1980-02-20",
  motivoConsulta: "Dolor en molar inferior", enfermedadActual: "Sensibilidad al frío hace 3 días",
  antecedentesPersonales: "Hipertensión controlada", examenClinico: "Caries profunda en 46",
  pronostico: "Favorable", alergiasRevisado: true, alergiasDetalle: "Penicilina", medicacionRevisado: true,
  antecedentesFamiliaresRevisado: true, completedAt: "2026-09-01T15:00:00Z",
  diagnosticos: [{ id: 1, cie10Code: "K02.1", ubicacion: "PIEZA", fdiNumber: 46, tipoDiagnostico: "DEFINITIVO", createdAt: "2026-09-01T14:00:00Z" }],
};
const CHART = {
  id: 1, patientId: 1, type: "INITIAL", status: "SEALED", examinedOn: "2026-09-01", sealedAt: "2026-09-01T15:00:00Z",
  specifications: "Corona 16: metal-porcelana", specificationsReviewed: true, observations: null, observationsReviewed: true,
  findings: [
    { id: 1, blockId: 1, type: "CORONA", subtype: "CC", condition: "GOOD", location: { locationKind: "TOOTH", toothA: 16 } },
    { id: 2, blockId: 1, type: "DIASTEMA", subtype: "", location: { locationKind: "BETWEEN_TEETH", toothA: 11, toothB: 21 } },
  ],
};
const CATALOG = [
  { type: "CORONA", subtype: "CC", acronym: "CC", scope: "TOOTH", colorBehavior: "SEGUN_ESTADO", requiresGrade: false, requiresSpecification: true },
  { type: "DIASTEMA", subtype: "", acronym: "", scope: "BETWEEN_TEETH", colorBehavior: "SIEMPRE_AZUL", requiresGrade: false, requiresSpecification: false },
];
const NOTES = [
  {
    id: 9, patientId: 1, status: "SELLADA", attendedAt: "2026-09-15T14:00:00Z", reason: "Control", examination: "Encía sana",
    treatment: "Profilaxis", nextAppointmentOn: "2026-10-15", sealedAt: "2026-09-15T15:00:00Z", sealedBy: 7,
    createdAt: "2026-09-15T15:00:00Z", createdBy: 7,
    diagnoses: [{ id: 3, cie10Code: "K05.1", ubicacion: "GENERAL", tipoDiagnostico: "PRESUNTIVO" }],
    backgroundEntries: [{ id: 5, category: "MEDICACION", kind: "ALTA", description: "Losartán" }],
    corrections: [{ id: 1, field: "EXAMINATION", correctedText: "Encía sana; placa en 31", reason: "Faltó la pieza", createdAt: "2026-09-16T10:00:00Z" }],
  },
  {
    id: 10, patientId: 1, status: "ABIERTA", attendedAt: "2026-10-09T14:00:00Z", reason: "Dolor al masticar",
    createdAt: "2026-10-09T14:05:00Z", createdBy: 7, diagnoses: [], backgroundEntries: [], corrections: [],
  },
];
const BACKGROUND = {
  items: [
    { category: "ALERGIA_RAM", source: "HISTORIA", description: "Penicilina", recordedAt: "2026-09-01T15:00:00Z" },
    { category: "ALERGIA_RAM", source: "FICHA", description: "Látex" },
    { category: "MEDICACION", source: "NOTA", description: "Losartán", recordedAt: "2026-09-15T14:00:00Z", noteId: 9, entryId: 5 },
  ],
};

const HOURS = {
  timezone: "America/Lima",
  days: ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"].map((day) => ({
    day,
    segments: day === "SUNDAY" ? [] : [{ opens: "09:00", closes: "13:00" }, { opens: "15:00", closes: "19:00" }],
  })),
};
const APPOINTMENTS = [
  { id: 1, patientId: 1, patientName: "Armando Lios Con Un Apellido Muy Largo", dentistUserId: 7,
    dentistName: "Odontóloga Con Un Nombre Bastante Largo", startsAt: "2026-10-20T15:00:00Z",
    endsAt: "2026-10-20T15:30:00Z", durationMinutes: 30, status: "AGENDADA" },
];

async function mockApi(page: Page, { authed, preset }: { authed: boolean; preset: string | null }) {
  await page.route("**/*", async (route) => {
    const req = route.request();
    if (!["fetch", "xhr"].includes(req.resourceType())) return route.fallback();
    const path = new URL(req.url()).pathname;
    const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (path.endsWith("/auth/refresh")) {
      return authed ? json({ accessToken: "t", user: USER }) : json({ type: "/errors/unauthorized", status: 401 }, 401);
    }
    if (path.endsWith("/organizations/me/branding")) return json(preset ? { brandPreset: preset } : {});
    if (path.endsWith("/dental-chart/catalog")) return json(CATALOG);
    // Pantalla Personal (add-staff): nombres y correos largos ponen a prueba el desborde a 375 px.
    if (path.endsWith("/staff/members")) return json([
      { id: 1, fullName: "Administradora Con Un Nombre Bastante Largo", email: "administradora.con.correo.largo@clinica-dental.test", role: "ADMIN", status: "ACTIVE" },
      { id: 2, fullName: "María Fernanda Ruiz", email: "maria.fernanda@clinica.test", role: "MEMBER", status: "INACTIVE" },
    ]);
    if (path.endsWith("/staff/invitations")) return json([
      { id: 10, email: "invitada.con.un.correo.muy.largo@clinica-dental.test", role: "MEMBER", state: "EXPIRED",
        createdAt: "2026-10-01T15:00:00Z", expiresAt: "2026-10-08T15:00:00Z", manageable: true },
    ]);
    if (/\/patients$/.test(path)) {
      return json({ content: [
        { id: 1, fullName: "Armando Lios", document: "12345678", birthDate: "1980-02-20", minor: false, phone: "+51987654321", status: "ACTIVE" },
        { id: 2, fullName: "Lucía Paz", document: "87654321", birthDate: "2015-06-01", minor: true, phone: "+51911122233", status: "ACTIVE" },
      ], page: 0, size: 20, totalElements: 2, totalPages: 1, last: true });
    }
    // Agenda y horario (add-appointments): nombres largos y un turno partido ponen a prueba el desborde.
    if (path.endsWith("/business-hours")) return json(HOURS);
    if (path.endsWith("/appointments/dentists")) return json([{ userId: 7, fullName: "Odontóloga Con Un Nombre Bastante Largo" }]);
    if (/\/appointments$/.test(path)) return json(APPOINTMENTS);
    if (/\/patients\/1$/.test(path)) return json(PATIENT);
    if (path.endsWith("/clinical-history/enablement")) return json({ status: "COMPLETED", enabledForTreatment: true });
    if (path.endsWith("/clinical-history")) return json(HISTORY);
    if (path.endsWith("/dental-chart")) return json(CHART);
    if (path.endsWith("/consultation-notes")) return json(NOTES);
    if (path.endsWith("/clinical-background")) return json(BACKGROUND);
    return json({});
  });
}

const PRIVATE = [
  "/", "/patients", "/patients/new", "/patients/1", "/patients/1/clinical-history",
  "/patients/1/dental-chart", "/patients/1/consultations", "/settings/branding",
  "/settings/staff", "/settings/staff?tab=invitaciones", "/appointments", "/settings/business-hours",
];

for (const width of [375, 768, 1280]) {
  test(`a ${width} px ninguna pantalla privada se desplaza de costado y la navegación es alcanzable`, async ({ page }) => {
    // Recorre todas las pantallas privadas esperando `networkidle` en cada una: más que los 30 s por defecto.
    test.setTimeout(90_000);
    await page.setViewportSize({ width, height: 900 });
    await mockApi(page, { authed: true, preset: null });
    for (const url of PRIVATE) {
      await page.goto(url);
      await page.waitForLoadState("networkidle");
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow, `${url} se desplaza ${overflow} px de costado`).toBeLessThanOrEqual(0);
      const nav =
        width < 640
          ? page.getByRole("button", { name: "Abrir menú" })
          : page.getByRole("navigation", { name: "Principal" }).getByRole("link", { name: "Pacientes" });
      await expect(nav, `${url}: navegación no alcanzable`).toBeVisible();
    }
  });
}

test("a 2560 px el marco mide 1920 px y queda centrado, con la barra junto al contenido", async ({ page }) => {
  await page.setViewportSize({ width: 2560, height: 1200 });
  await mockApi(page, { authed: true, preset: null });
  await page.goto("/patients");
  const nav = page.getByRole("navigation", { name: "Principal" });
  await expect(nav).toBeVisible();
  const frame = await page.evaluate(() => {
    const main = document.querySelector("main")!;
    // El marco es el ancestro común de la barra y el contenido.
    const box = main.parentElement!.parentElement!.getBoundingClientRect();
    return { left: box.left, width: box.width };
  });
  expect(frame.width).toBe(1920);
  expect(frame.left).toBe((2560 - 1920) / 2);
  const navLeft = (await nav.boundingBox())!.x;
  expect(navLeft).toBeGreaterThanOrEqual(frame.left);
});

for (const width of [768, 1280]) {
  test(`a ${width} px la barra de íconos no muestra barras de scroll`, async ({ page }) => {
    // Escritorio: barra contraída por preferencia; tablet: siempre solo íconos.
    await page.addInitScript(() =>
      localStorage.setItem("odr-sidebar", JSON.stringify({ state: { collapsed: true }, version: 0 })),
    );
    await page.setViewportSize({ width, height: 900 });
    await mockApi(page, { authed: true, preset: null });
    await page.goto("/patients");
    await expect(page.getByRole("navigation", { name: "Principal" })).toBeVisible();
    const overflowing = await page.evaluate(() =>
      [...document.querySelectorAll<HTMLElement>("aside *")]
        .filter((el) => getComputedStyle(el).overflowX !== "visible" && el.scrollWidth > el.clientWidth)
        .map((el) => el.className),
    );
    expect(overflowing).toEqual([]);
  });
}

// update-responsive-layout: el odontograma conserva un ancho mínimo legible y se desliza dentro de
// su tarjeta; sus rótulos de fila quedan a la altura de su fila a cualquier ancho.
test.describe("odontograma en pantallas angostas", () => {
  test.beforeEach(async ({ page }) => {
    CHART.status = "OPEN";
    // La guía ya vista: estos tests miden el dibujo, no el recorrido (update-dental-chart-guidance).
    await page.addInitScript(() =>
      localStorage.setItem("odr-guidance", JSON.stringify({ state: { seen: { "dental-chart": [7] } }, version: 0 })),
    );
  });

  test("a 375 px el dibujo mide al menos 600 px, se desliza dentro de su tarjeta y lo indica", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 900 });
    await mockApi(page, { authed: true, preset: null });
    await page.goto("/patients/1/dental-chart");
    const scroller = page.getByTestId("odontogram-scroll");
    await expect(scroller).toBeVisible();
    const m = await scroller.evaluate((el) => ({
      scrollWidth: el.scrollWidth,
      clientWidth: el.clientWidth,
      drawing: el.firstElementChild!.getBoundingClientRect().width,
      page: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    }));
    expect(m.drawing).toBeGreaterThanOrEqual(600);
    expect(m.scrollWidth).toBeGreaterThan(m.clientWidth);
    expect(m.page).toBeLessThanOrEqual(0);
    await expect(page.getByText("Desliza para ver toda la boca")).toBeVisible();
  });

  test("a 1280 px el dibujo entra completo, sin deslizar ni indicación", async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await mockApi(page, { authed: true, preset: null });
    await page.goto("/patients/1/dental-chart");
    const scroller = page.getByTestId("odontogram-scroll");
    await expect(scroller).toBeVisible();
    const fits = await scroller.evaluate((el) => el.scrollWidth <= el.clientWidth);
    expect(fits).toBe(true);
    await expect(page.getByText("Desliza para ver toda la boca")).toHaveCount(0);
  });

  for (const width of [375, 768, 1280]) {
    test(`a ${width} px cada rótulo de fila queda a la altura de su fila`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await mockApi(page, { authed: true, preset: null });
      await page.goto("/patients/1/dental-chart");
      await expect(page.getByTestId("odontogram-scroll")).toBeVisible();
      // Una pieza visible de cada fila (permanente sup., temporal sup., temporal inf., permanente inf.).
      const rows = [17, 54, 84, 47];
      const misaligned = await page.evaluate((teeth) => {
        const labels = [...document.querySelectorAll<HTMLElement>("[data-testid=odontogram-scroll] .sticky > div")];
        return teeth.flatMap((fdi, i) => {
          const surfaces = [...document.querySelectorAll(`[aria-label^="Pieza ${fdi}, superficie"]`)];
          const top = Math.min(...surfaces.map((s) => s.getBoundingClientRect().top));
          const bottom = Math.max(...surfaces.map((s) => s.getBoundingClientRect().bottom));
          const label = labels[i].getBoundingClientRect();
          const center = (label.top + label.bottom) / 2;
          // Holgura de media corona: el rótulo va "dentro" de la corona, no exactamente centrado.
          const slack = (bottom - top) / 2;
          return center >= top - slack && center <= bottom + slack ? [] : [`${labels[i].textContent} (${fdi}): ${center} fuera de ${top}-${bottom}`];
        });
      }, rows);
      expect(misaligned).toEqual([]);
    });
  }
});

// update-dental-chart-guidance: la guía se abre sola sobre un odontograma abierto la primera vez, y el recorte de
// cada paso queda sobre su ancla, a la vista y sin desplazar la página de costado.
test.describe("guía del odontograma", () => {
  test.beforeEach(() => {
    CHART.status = "OPEN";
  });

  for (const width of [375, 768, 1280]) {
    test(`a ${width} px el recorte queda sobre la pieza resaltada y la página no se desplaza de costado`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await mockApi(page, { authed: true, preset: null });
      await page.goto("/patients/1/dental-chart");
      const tour = page.getByRole("dialog", { name: "¿Dónde registro un hallazgo?" });
      await expect(tour).toBeVisible();
      // El catálogo del mock solo trae CORONA (pieza) y DIASTEMA (entre piezas): el paso 2 es "Toda la pieza".
      await page.getByRole("button", { name: "Siguiente" }).click();
      await expect(page.getByRole("dialog", { name: "Toda la pieza" })).toBeVisible();

      const anchor = page.locator('[data-guide="tooth-number"]');
      const ring = page.locator("[data-testid=tour-shield] rect.stroke-primary");
      await expect(ring).toHaveCount(1);
      await expect
        .poll(async () => {
          const [a, r] = await Promise.all([anchor.boundingBox(), ring.boundingBox()]);
          if (!a || !r) return false;
          const slack = 2;
          return r.x <= a.x + slack && r.y <= a.y + slack && r.x + r.width >= a.x + a.width - slack && r.y + r.height >= a.y + a.height - slack;
        }, { message: "el recorte no rodea el número de la pieza 16" })
        .toBe(true);

      const anchorBox = (await anchor.boundingBox())!;
      expect(anchorBox.x).toBeGreaterThanOrEqual(0);
      expect(anchorBox.x + anchorBox.width).toBeLessThanOrEqual(width);
      const card = (await page.getByRole("dialog", { name: "Toda la pieza" }).boundingBox())!;
      expect(card.x).toBeGreaterThanOrEqual(0);
      expect(card.x + card.width).toBeLessThanOrEqual(width);
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(0);
    });
  }

  for (const width of [375, 1280]) {
    test(`a ${width} px el selector de tipo y el modo ubicar caben sin desplazar la página de costado`, async ({ page }) => {
      await page.addInitScript(() =>
        localStorage.setItem("odr-guidance", JSON.stringify({ state: { seen: { "dental-chart": [7] } }, version: 0 })),
      );
      await page.setViewportSize({ width, height: 900 });
      await mockApi(page, { authed: true, preset: null });
      await page.goto("/patients/1/dental-chart");
      await page.getByRole("button", { name: "Pieza 15, sin hallazgos" }).click();
      const dialog = page.getByRole("dialog", { name: "Agregar hallazgo" });
      await expect(dialog).toBeVisible();
      // Un solo click abre la lista: el error de validación no aparece mientras se elige (D5).
      await page.getByRole("combobox", { name: /Tipo de hallazgo/ }).click();
      await expect(page.getByRole("listbox")).toBeVisible();
      await expect(page.getByRole("alert")).toHaveCount(0);
      const list = (await page.getByRole("listbox").boundingBox())!;
      expect(list.x).toBeGreaterThanOrEqual(0);
      expect(list.x + list.width).toBeLessThanOrEqual(width);

      await page.getByRole("option", { name: /^Diastema/ }).click();
      await page.getByRole("button", { name: "Elegir en el odontograma" }).click();
      await expect(page.getByText("Ubicando: Diastema")).toBeVisible();
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
      expect(overflow).toBeLessThanOrEqual(0);
    });
  }
});

// update-consultation-note-findings: el panel de hallazgos de la nota abierta, con un pendiente rojo, cabe a cualquier
// ancho. Las rutas se agregan después de mockApi: Playwright usa la última que coincide.
for (const width of [375, 1280]) {
  test(`a ${width} px el panel de hallazgos de la nota cabe sin desplazar la página de costado`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockApi(page, { authed: true, preset: null });
    const json = (body: unknown) => ({ status: 200, contentType: "application/json", body: JSON.stringify(body) });
    await page.route("**/patients/1/dental-chart", (route) =>
      route.fulfill(json({
        ...CHART,
        findings: [
          ...CHART.findings,
          { id: 3, blockId: 1, type: "LESION_CARIES", subtype: "CD", location: { locationKind: "SURFACE", toothA: 46, surface: "OCLUSAL" } },
        ],
      })),
    );
    await page.route("**/dental-chart/catalog", (route) =>
      route.fulfill(json([...CATALOG, { type: "LESION_CARIES", subtype: "CD", acronym: "CD", scope: "SURFACE", colorBehavior: "SIEMPRE_ROJO", requiresGrade: false, requiresSpecification: false }])),
    );
    await page.goto("/patients/1/consultations");

    const pending = page.getByRole("list", { name: "Hallazgos pendientes" });
    await expect(pending).toContainText("Pieza 46, cara oclusal");
    await page.getByRole("button", { name: /Ver los demás hallazgos/ }).click();
    await expect(page.getByRole("list", { name: "Otros hallazgos del odontograma" })).toBeVisible();

    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow, `se desplaza ${overflow} px de costado`).toBeLessThanOrEqual(0);
  });
}

// update-dental-chart-touch: con pantalla táctil, tocar una pieza abre la pieza ampliada; cada opción mide >= 44 px y
// elegir una cara abre su registro. Con la guía ya vista para que no tape el gráfico.
test.describe("odontograma con el dedo", () => {
  test.use({ hasTouch: true });

  test.beforeEach(async ({ page }) => {
    CHART.status = "OPEN";
    await page.addInitScript(() =>
      localStorage.setItem("odr-guidance", JSON.stringify({ state: { seen: { "dental-chart": [7] } }, version: 0 })),
    );
  });

  test("a 375 px tocar una pieza la amplía con zonas de al menos 44 px y elegir una cara abre su registro", async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 });
    await mockApi(page, { authed: true, preset: null });
    await page.goto("/patients/1/dental-chart");
    await page.getByRole("button", { name: /^Pieza 15, superficie oclusal/ }).tap();

    const panel = page.getByRole("dialog", { name: "Pieza 15" });
    await expect(panel).toBeVisible();
    const targets = [
      ...(await panel.getByRole("button", { name: /^Cara / }).all()),
      ...(await panel.getByRole("button", { name: /^Toda la pieza|^Entre la/ }).all()),
    ];
    expect(targets.length).toBe(8); // 5 caras + pieza completa + 2 espacios
    for (const target of targets) {
      const box = (await target.boundingBox())!;
      expect(Math.min(box.width, box.height), await target.getAttribute("aria-label") ?? await target.textContent() ?? "").toBeGreaterThanOrEqual(44);
    }
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    expect(overflow).toBeLessThanOrEqual(0);

    await panel.getByRole("button", { name: /^Cara oclusal de la 15/ }).tap();
    await expect(page.getByRole("dialog", { name: "Agregar hallazgo" })).toBeVisible();
    await expect(page.getByRole("dialog", { name: "Agregar hallazgo" })).toContainText("Pieza 15, superficie oclusal");
  });
});
