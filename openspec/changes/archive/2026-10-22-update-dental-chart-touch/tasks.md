# Tasks

> Solo frontend. Antes de tocar el frontend, skill `frontend-guard`. Pantalla autenticada: sistema de diseño existente
> (`docs/frontend.md` §4); sobre el papel del odontograma, paleta fija (`chartPalette`), nunca tokens del tema. Tests
> derivados de los Scenario del delta.

## 1. Geometría y panel

- [x] 1.1 Mover `crownSlots` a `dental-chart/utils/toothGeometry.ts` (sin cambio visual) con un parámetro de proporción mínima de la cara central para el panel (design D3); test de que en el panel toda cara, también la incisal, mide ≥ 44 px a 240 px de lado
- [x] 1.2 `ToothMagnifier` (design D3): `Sheet` inferior con la pieza ampliada (caras con nombre, color de sus hallazgos), "Toda la pieza", los espacios con las vecinas de la misma fila (una sola en los extremos), "Ya registrado" con `describeFinding`; `max-h-[85vh]` + `overflow-y-auto` + safe-area de iOS; rótulos con `pointerEvents="none"`; foco inicial en el título; elegir llama a `onSelectLocation` y cierra; cerrar no registra
- [x] 1.3 Modos en el panel (design D4): en ubicar `SURFACE` solo caras y en `BETWEEN_TEETH` solo espacios, el resto atenuado, sin `tabIndex` y con `aria-disabled`

## 2. Odontograma

- [x] 2.1 `Odontogram` registra el `pointerType` de cada pieza y, con `touch`, abre `ToothMagnifier` en vez del click directo (design D1, D2); el `click` consume y borra el tipo guardado; `activateOnEnterOrSpace` va directo y no hereda un `touch` anterior; mouse, lápiz y teclado sin cambios
- [x] 2.2 Modos al tocar (design D4): ubicar `TOOTH` va directo a la pieza completa; en ubicar `BETWEEN_TEETH` la pieza responde al toque y abre el panel solo con sus espacios; rango ancla sin panel; ubicar `ARCH` y odontograma sellado no hacen nada
- [x] 2.3 Guía: si el último toque fue con el dedo, el paso de caras dice "toca la pieza y elige la cara" (design Risks); si complica, anotarlo y consultar al usuario — *apply: en vez de detectar el último toque (la guía no lo conoce), el paso de caras suma una frase para el celular que vale en los dos casos*

## 3. Tests y cierre

- [x] 3.1 Tests (Vitest; para el toque, `fireEvent.pointerDown(el, { pointerType: "touch" })` + `fireEvent.click(el)`, determinista en jsdom sin layout de SVG; `userEvent` para mouse y teclado) de cada Scenario: tocar amplía con caras, pieza y espacios; elegir la oclusal abre su registro; lo ya registrado se lista y marca; cerrar no registra; con mouse va directo; elegir un espacio; pieza de extremo con una vecina; teclado tras un toque va directo; ubicar superficie solo ofrece caras; ubicar pieza completa va directo; ubicar entre piezas ofrece solo espacios; rango ancla sin panel; sellado no amplía
- [x] 3.2 e2e con `hasTouch` a 375 px: `tap` en una pieza abre el panel, ninguna opción mide menos de 44 px y elegir una cara abre su registro; sin desplazamiento lateral
- [x] 3.3 `pnpm validate` (con `--maxWorkers=8` en local) y e2e verdes; captura para el usuario antes de commitear; prueba manual en un celular real
- [x] 3.4 `docs/frontend.md` §4.3: zonas táctiles chicas en dibujos densos → panel ampliado por toque (`pointerType`); §4.1: sacar `sheet` de "Por incorporar" (ya existe y lo usa `MobileNav`); `openspec validate update-dental-chart-touch --strict` y medir a mano el requirement ≤ 500 caracteres
