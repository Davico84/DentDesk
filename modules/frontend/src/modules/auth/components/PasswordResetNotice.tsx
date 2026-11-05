import { CheckCircle2, Info } from "lucide-react";
import { useLocation } from "react-router-dom";
import { cn } from "@/modules/core/utils/cn";
import { passwordResetMessage, readPasswordReset } from "./passwordResetDisplay";

/** Aviso del login tras restablecer la contraseña: listo, bloqueada hasta HH:MM o acceso desactivado (D7). */
export function PasswordResetNotice() {
  const result = readPasswordReset(useLocation().state);
  if (!result) {
    return null;
  }
  const ok = result.outcome === "OK";
  const Icon = ok ? CheckCircle2 : Info;
  return (
    <p
      role="status"
      className={cn(
        "flex items-start gap-2 rounded-lg border px-3 py-2.5 text-sm",
        ok ? "border-primary/30 bg-primary/5 text-foreground" : "border-border bg-muted text-foreground",
      )}
    >
      <Icon className={cn("mt-0.5 size-4 shrink-0", ok ? "text-primary" : "text-muted-foreground")} aria-hidden />
      {passwordResetMessage(result)}
    </p>
  );
}
