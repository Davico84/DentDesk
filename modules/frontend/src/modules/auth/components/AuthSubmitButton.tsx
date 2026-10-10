import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { cn } from "@/modules/core/utils/cn";

interface AuthSubmitButtonProps {
  pending: boolean;
  pendingLabel: string;
  children: ReactNode;
  className?: string;
}

/**
 * Botón submit de login/register: morado FIJO (no `bg-primary`), a juego con el panel de
 * marca de `AuthLayout` (también fijo) en los dos modos — si uno sigue el token y el otro no,
 * se desalinean en modo oscuro (`--primary` cambia de valor ahí).
 */
export function AuthSubmitButton({ pending, pendingLabel, children, className }: AuthSubmitButtonProps) {
  return (
    <button
      type="submit"
      disabled={pending}
      className={cn(
        "flex h-11 w-full items-center justify-center gap-2 rounded-lg",
        "bg-brand-cta text-sm font-semibold text-white",
        "transition-colors duration-150",
        "hover:bg-brand-cta-hover",
        "active:scale-[0.98]",
        "focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2",
        "disabled:pointer-events-none disabled:opacity-60",
        className,
      )}
    >
      {pending ? (
        <>
          <Loader2 className="size-4 animate-spin" />
          <span>{pendingLabel}</span>
        </>
      ) : (
        <span>{children}</span>
      )}
    </button>
  );
}
