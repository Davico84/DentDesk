/** Utilidades de presentación de pacientes (compartidas entre listado, ficha y detalle). */

/** Iniciales (una o dos) a partir del nombre, para el avatar. */
export function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

/** Edad legible ("34 años", "8 meses", "recién nacido") a partir de `YYYY-MM-DD`. */
export function ageLabel(birthDate: string): string {
  if (!birthDate) return "";
  const born = new Date(birthDate);
  if (Number.isNaN(born.getTime())) return "";
  const now = new Date();
  let years = now.getFullYear() - born.getFullYear();
  const m = now.getMonth() - born.getMonth();
  if (m < 0 || (m === 0 && now.getDate() < born.getDate())) years--;
  if (years >= 1) return `${years} ${years === 1 ? "año" : "años"}`;

  let months = now.getMonth() - born.getMonth() + (now.getDate() < born.getDate() ? -1 : 0);
  if (months < 0) months += 12;
  if (months >= 1) return `${months} ${months === 1 ? "mes" : "meses"}`;
  return "recién nacido";
}
