import { initials } from "./patientDisplay";

type PatientAvatarProps = {
  fullName: string;
  /** Atenúa el avatar para pacientes dados de baja. */
  muted?: boolean;
  className?: string;
};

/**
 * Avatar circular con las iniciales del paciente. Usa los tokens de marca (`primary`), así que
 * hereda el color de cada clínica y respeta el dark mode — nada hardcodeado.
 */
export function PatientAvatar({ fullName, muted = false, className = "" }: PatientAvatarProps) {
  return (
    <span
      aria-hidden="true"
      className={[
        "flex size-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold",
        muted ? "bg-muted text-muted-foreground" : "bg-primary/10 text-primary",
        className,
      ].join(" ")}
    >
      {initials(fullName)}
    </span>
  );
}
