> Solo frontend. Antes de crear o modificar cada componente, hook o pantalla: skill `frontend-guard`. La pasada visual de la superficie nueva: skill `frontend-design` (design D9).

## 1. Base

- [x] 1.1 `core/config/sections.ts` (`home`, `patients`, `branding` con `["ADMIN"]`) + `canAccess`; tests (design D1; scenarios *Ítem sin roles visible para todos*, *Menú y rutas derivados de la misma configuración*)
- [x] 1.2 `core/ui/sheet` con `pnpm dlx shadcn@latest add sheet`; verificar con `git diff` que no tocó `globals.css`
- [x] 1.3 `store/useSidebarStore` (`collapsed`, `toggle`, `persist` con caída a memoria) + tests (design D5)

## 2. Shell

- [x] 2.1 `core/components/shell`: `NavItem` (con tooltip opcional y `aria-label`), `Sidebar` (logo por prop, secciones filtradas por rol, cerrar sesión al pie, botón contraer ≥ 1024 px), `Header` (usuario o correo, rol, clínica, tema, botón de menú en celular), `MobileNav` (sobre `Sheet`, se cierra al navegar)
- [x] 2.2 `layouts/AppLayout` (marco de 1920 px, barra `sticky`, único `<main>`) y routing anidado bajo `RequireAuth`, con las secciones de D1 (design D2/D6)
- [x] 2.3 `PageContainer` pasa a `<div>` sin `min-h-screen`; `AccessDenied` variante `inShell` con foco en el título (design D3)
- [ ] 2.4 Pasada visual con `frontend-design`: barra, cabecera, cajón y estados (activo, hover, foco, contraída) en claro/oscuro y con un preset de marca (design D9)
- [x] 2.5 Tests de `AppLayout` y componentes: un solo `<main>`; login sin shell; ítem activo en subrutas; ítem de Marca oculto para `USER`; acceso denegado dentro del shell con foco y sin pedir datos; identidad con y sin nombre; cerrar sesión deshabilitado en curso; cajón (abrir con `aria-expanded`, cerrar al navegar, con `Escape` y con clic fuera); tooltip en la barra de íconos sin `title`; contraer/expandir con `aria-expanded`, preferencia recordada y sin almacenamiento; botón oculto bajo 1024 px

## 3. Inicio

- [x] 3.1 `HomeScreen` dentro del shell: saludo con nombre y clínica, atajos a Nuevo paciente, Buscar paciente y Marca (si `canAccess`); sin cerrar sesión (design D7)
- [x] 3.2 Tests: Inicio de `USER` sin atajo a Marca; Inicio de `ADMIN` con el atajo

## 4. Listado de pacientes

- [x] 4.1 `PatientList` con `q`, `pagina` e `inactivos` en la URL (`useSearchParams`, `replace` al tipear) y registro de la última URL en `sessionStorage` (design D8)
- [x] 4.2 `patientsListPath()` en los enlaces de volver (ficha, formulario, no encontrado); limpieza en el logout
- [x] 4.3 Tests por scenario de *El listado conserva la búsqueda al volver*

## 5. Verificación y docs

- [ ] 5.1 e2e Playwright (API mockeada): a 375, 768 y 1280 px, ninguna pantalla privada se desplaza de costado y la navegación es alcanzable; a 2560 px el marco mide 1920 px y queda centrado
- [ ] 5.2 Capturas antes/después de las pantallas existentes: el contenido no cambia salvo por el shell alrededor
- [ ] 5.3 `pnpm validate` verde; `pnpm build`
- [ ] 5.4 `docs/frontend.md` §1 (shell en la estructura) y §4.3 (el `AppLayout` existe: quitar "objetivo, aún no adoptado"; breakpoints reales), `docs/vision.md` §8
- [ ] 5.5 `openspec validate add-app-shell --strict`
