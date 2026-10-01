// ⚠️ Colores NORMATIVOS, no del tema (design.md D10, ADR-0013 D5): NTS 188 fija rojo/azul exactos
// y el odontograma —y su lista de hallazgos— se renderiza siempre sobre fondo blanco con texto oscuro
// fijo, aunque la app esté en modo oscuro: es un documento legal, lo mostrado debe ser idéntico a lo
// auditado. Única excepción admitida a la regla de `frontend-guard` de no hardcodear colores: TODA la
// paleta (y la tipografía, ver `index.html`) del documento vive acá, nombrada, no dispersa — salvo
// RED/BLUE, en `findingColor.ts` junto a la regla que los elige.
export const PAPER = "#FFFFFF";
export const PAPER_ALT = "#F3F4F6"; // cabecera de la lista de hallazgos — INK_2 sobre él 7.6:1
export const INK = "#16181D"; // texto principal, foco, selección — 17.8:1
export const INK_2 = "#4A4F57"; // texto secundario — 8.2:1
export const INK_3 = "#6B7079"; // FDI sin hallazgo, rótulos — 5.0:1
export const OUTLINE = "#8C929B"; // bordes de superficie — 3.1:1
export const DIVIDER = "#D5D8DC"; // recuadro vacío, líneas guía, separador de filas
export const SELECTION = "#7C3AED"; // resalte de UI transitorio (modo rango) — no es un color clínico/normativo
export const SELECTION_SOFT = "#F5F0FF"; // fondo del aviso del modo rango — INK sobre él 16.6:1, SELECTION 5.4:1

export const FONT_BODY = '"Atkinson Hyperlegible Next", system-ui, sans-serif';
export const FONT_MONO = '"Atkinson Hyperlegible Mono", ui-monospace, monospace';
