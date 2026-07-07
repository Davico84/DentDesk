/** Nivel de fuerza de la contraseña (0 = vacía, 1–4 de débil a fuerte). */
export interface PasswordStrength {
  level: 0 | 1 | 2 | 3 | 4;
  label: string;
  /** Clase Tailwind del color para las barras y el texto (bg-* y text-* separados por espacio). */
  colorClass: string;
}

/** Evalúa la fuerza de una contraseña según 4 criterios independientes. */
export function getPasswordStrength(password: string): PasswordStrength {
  if (!password) return { level: 0, label: "", colorClass: "" };

  let score = 0;
  if (password.length >= 8) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;

  if (score === 1) return { level: 1, label: "Débil",   colorClass: "bg-destructive text-destructive" };
  if (score === 2) return { level: 2, label: "Regular", colorClass: "bg-[hsl(38_92%_46%)] text-[hsl(38_92%_46%)]" };
  if (score === 3) return { level: 3, label: "Buena",   colorClass: "bg-[hsl(174_70%_40%)] text-[hsl(174_70%_40%)]" };
  return             { level: 4, label: "Fuerte",  colorClass: "bg-[hsl(152_55%_38%)] text-[hsl(152_55%_38%)]" };
}
