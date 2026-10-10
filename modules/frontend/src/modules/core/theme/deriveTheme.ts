/**
 * Deriva el tema del app shell a partir de 1-2 colores de marca (design.md D3/D4).
 *
 * - **Marca:** `primary`, `ring`, `secondary`/`accent` y sus `-foreground` salen del color de marca.
 * - **`-foreground` por contraste (WCAG AA):** el texto sobre el color de marca alterna ÚNICAMENTE
 *   entre blanco y el neutro oscuro del sistema; nunca se generan colores nuevos.
 * - **Clamping de luminosidad por modo:** un primario muy claro no se pierde en modo claro; uno muy
 *   oscuro ilumina lo justo en modo oscuro.
 * - **Neutros:** conservan S y L del producto y solo adoptan el *hue* de la marca (cohesión sin
 *   tocar contraste). **Semánticos** (éxito/aviso/error): intactos (no se sobreescriben).
 * - Sin `brandPrimary` → `null` (el app shell usa los valores por defecto del producto).
 */

export interface BrandColors {
  brandPrimary?: string | null;
  brandSecondary?: string | null;
}

export type TokenMap = Record<string, string>; // nombre de token (sin `--`) → color completo "hsl(H S% L%)"

export interface DerivedTheme {
  light: TokenMap;
  dark: TokenMap;
  /** true si el color de marca no alcanza contraste AA como primario (para avisar en el editor). */
  contrastWarning: boolean;
}

interface Hsl {
  h: number;
  s: number;
  l: number;
}
interface Rgb {
  r: number;
  g: number;
  b: number;
}

/** Neutros del producto (S/L por modo); el hue se reemplaza por el de la marca. */
const NEUTRAL_BASE: Record<"light" | "dark", Record<string, { s: number; l: number }>> = {
  light: {
    background: { s: 0, l: 100 },
    foreground: { s: 26, l: 12 },
    card: { s: 0, l: 100 },
    "card-foreground": { s: 26, l: 12 },
    popover: { s: 0, l: 100 },
    "popover-foreground": { s: 26, l: 12 },
    muted: { s: 20, l: 96 },
    "muted-foreground": { s: 10, l: 42 },
    border: { s: 16, l: 89 },
    input: { s: 16, l: 89 },
  },
  dark: {
    background: { s: 33, l: 8 },
    foreground: { s: 14, l: 94 },
    card: { s: 30, l: 11 },
    "card-foreground": { s: 14, l: 94 },
    popover: { s: 30, l: 11 },
    "popover-foreground": { s: 14, l: 94 },
    muted: { s: 22, l: 17 },
    "muted-foreground": { s: 12, l: 66 },
    border: { s: 20, l: 22 },
    input: { s: 20, l: 22 },
  },
};

const AA_CONTRAST = 4.5;

export function hexToHsl(hex: string): Hsl {
  return rgbToHsl(hexToRgb(hex));
}

function hexToRgb(hex: string): Rgb {
  const n = hex.replace("#", "");
  return {
    r: parseInt(n.slice(0, 2), 16),
    g: parseInt(n.slice(2, 4), 16),
    b: parseInt(n.slice(4, 6), 16),
  };
}

function rgbToHsl({ r, g, b }: Rgb): Hsl {
  const rn = r / 255;
  const gn = g / 255;
  const bn = b / 255;
  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const d = max - min;
  let h = 0;
  if (d !== 0) {
    if (max === rn) h = ((gn - bn) / d) % 6;
    else if (max === gn) h = (bn - rn) / d + 2;
    else h = (rn - gn) / d + 4;
    h *= 60;
    if (h < 0) h += 360;
  }
  const l = (max + min) / 2;
  const s = d === 0 ? 0 : d / (1 - Math.abs(2 * l - 1));
  return { h, s: s * 100, l: l * 100 };
}

function hslToRgb({ h, s, l }: Hsl): Rgb {
  const sn = s / 100;
  const ln = l / 100;
  const c = (1 - Math.abs(2 * ln - 1)) * sn;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = ln - c / 2;
  const [r, g, b] =
    h < 60
      ? [c, x, 0]
      : h < 120
        ? [x, c, 0]
        : h < 180
          ? [0, c, x]
          : h < 240
            ? [0, x, c]
            : h < 300
              ? [x, 0, c]
              : [c, 0, x];
  return { r: Math.round((r + m) * 255), g: Math.round((g + m) * 255), b: Math.round((b + m) * 255) };
}

function relativeLuminance({ r, g, b }: Rgb): number {
  const channel = (v: number) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function contrastRatio(a: Rgb, b: Rgb): number {
  const la = relativeLuminance(a);
  const lb = relativeLuminance(b);
  const [hi, lo] = la >= lb ? [la, lb] : [lb, la];
  return (hi + 0.05) / (lo + 0.05);
}

/** Color completo, mismo formato que los tokens de `globals.css` (update-tailwind-v4, design D3). */
const hslStr = ({ h, s, l }: Hsl): string => `hsl(${Math.round(h)} ${Math.round(s)}% ${Math.round(l)}%)`;

/** Acota la luminosidad del primario para que destaque legible en cada modo. */
function clampedPrimary(base: Hsl, mode: "light" | "dark"): Hsl {
  let l = base.l;
  if (mode === "light" && l > 85) l = 80;
  if (mode === "dark" && l < 50) l = 55 + (l / 50) * 10; // 0%→55%, 50%→65%
  return { h: base.h, s: base.s, l };
}

/** Elige el `-foreground` (blanco o neutro oscuro) que maximiza el contraste sobre el primario. */
function foregroundFor(primary: Hsl): { fg: Hsl; passesAA: boolean } {
  const primaryRgb = hslToRgb(primary);
  const white: Hsl = { h: 0, s: 0, l: 100 };
  const dark: Hsl = { h: primary.h, s: 26, l: 12 }; // neutro oscuro del sistema, re-tintado
  const cWhite = contrastRatio(primaryRgb, hslToRgb(white));
  const cDark = contrastRatio(primaryRgb, hslToRgb(dark));
  const useWhite = cWhite >= cDark;
  return { fg: useWhite ? white : dark, passesAA: Math.max(cWhite, cDark) >= AA_CONTRAST };
}

export function deriveTheme(colors: BrandColors): DerivedTheme | null {
  if (!colors.brandPrimary) return null;

  const primaryBase = hexToHsl(colors.brandPrimary);
  const secondaryHue = colors.brandSecondary ? hexToHsl(colors.brandSecondary).h : primaryBase.h;
  let contrastWarning = false;

  const build = (mode: "light" | "dark"): TokenMap => {
    const primary = clampedPrimary(primaryBase, mode);
    const { fg, passesAA } = foregroundFor(primary);
    if (!passesAA) contrastWarning = true;

    const map: TokenMap = {};
    // Neutros: conservan S/L del producto, adoptan el hue de la marca.
    for (const [token, sl] of Object.entries(NEUTRAL_BASE[mode])) {
      map[token] = hslStr({ h: primaryBase.h, s: sl.s, l: sl.l });
    }
    // Marca.
    map.primary = hslStr(primary);
    map["primary-foreground"] = hslStr(fg);
    map.ring =
      mode === "light"
        ? hslStr({ h: primary.h, s: primary.s, l: Math.min(primary.l + 9, 60) })
        : hslStr(primary);

    // Secundario/acento (del secundario si existe; si no, del hue del primario).
    if (mode === "light") {
      map.secondary = hslStr({ h: secondaryHue, s: 44, l: 93 });
      map.accent = hslStr({ h: secondaryHue, s: 60, l: 92 });
      map["secondary-foreground"] = hslStr({ h: secondaryHue, s: 84, l: 20 });
      map["accent-foreground"] = hslStr({ h: secondaryHue, s: 84, l: 20 });
    } else {
      map.secondary = hslStr({ h: secondaryHue, s: 24, l: 18 });
      map.accent = hslStr({ h: secondaryHue, s: 26, l: 20 });
      map["secondary-foreground"] = hslStr({ h: secondaryHue, s: 60, l: 75 });
      map["accent-foreground"] = hslStr({ h: secondaryHue, s: 65, l: 72 });
    }
    return map;
  };

  return { light: build("light"), dark: build("dark"), contrastWarning };
}
