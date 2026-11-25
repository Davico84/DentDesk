import { AlertCircle } from "lucide-react";

/** Error del servidor que no es de un campo (choque, fuera de horario, odontólogo no habilitado…), arriba del pie. */
export function AgendaFormError({ message }: { message: string | null }) {
  if (!message) {
    return null;
  }
  return (
    <p role="alert" className="flex items-start gap-2 rounded-xl bg-destructive/10 px-3 py-2 text-sm text-destructive">
      <AlertCircle className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
      {message}
    </p>
  );
}
