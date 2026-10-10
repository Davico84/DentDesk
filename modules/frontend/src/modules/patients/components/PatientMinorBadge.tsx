/**
 * Píldora "menor": señala que el paciente requiere responsable. No es decoración — es la regla de
 * negocio hecha visible (design.md D2), y por eso se muestra junto al nombre.
 */
export function PatientMinorBadge() {
  return (
    <span className="inline-flex items-center rounded-full bg-accent px-2 py-0.5 text-xs font-medium text-accent-foreground">
      menor
    </span>
  );
}
