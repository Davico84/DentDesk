import { useEffect, useState } from "react";
import { Clock3, Sunrise, WifiOff } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { cn } from "@/modules/core/utils/cn";
import { useOnlineStatus } from "@/modules/core/hooks/useOnlineStatus";
import { STUCK_AFTER_S, WARMING_AFTER_S } from "@/modules/core/utils/serverWarmup";
import { supportContact } from "@/modules/core/config/supportContact";
import { useSlowRequestStore } from "@/store/useSlowRequestStore";
import { SupportRow } from "./SupportRow";

const TITLES = {
  waking: "Despertando el servidor…",
  stuck: "Está tardando más de lo normal",
  offline: "Sin conexión a internet",
} as const;

/** Segundos desde `since` (ms), recalculados cada segundo mientras haya una espera. */
function useSecondsSince(since: number | null): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (since === null) return;
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [since]);
  return since === null ? 0 : Math.max(0, Math.floor((now - since) / 1000));
}

/**
 * Aviso de espera durante el uso (`update-slow-request-notice` D2): si alguna petición a la API
 * pasa de unos segundos sin responder (el servidor de Render despertando), lo dice arriba de la
 * pantalla sin bloquearla, y se va solo cuando responde. Pasados 90 s ofrece reintentar y el
 * contacto de soporte; sin red, lo dice. Complementa la pantalla de arranque en frío, que cubre la
 * carga de la app. No mueve el foco: el usuario puede estar escribiendo.
 */
export function SlowRequestNotice({ reload = () => window.location.reload() }: { reload?: () => void }) {
  const slowSince = useSlowRequestStore.use.slowSince();
  const online = useOnlineStatus();
  // La petición se marca lenta al cumplir WARMING_AFTER_S: la espera total suma ese tramo.
  const seconds = useSecondsSince(slowSince) + WARMING_AFTER_S;

  if (slowSince === null) return null;

  const state = !online ? "offline" : seconds >= STUCK_AFTER_S ? "stuck" : "waking";
  const Icon = state === "offline" ? WifiOff : state === "stuck" ? Clock3 : Sunrise;

  return (
    // Celular: abajo, para no tapar la cabecera (botón del menú). Desde 640 px: arriba al centro.
    <div className="pointer-events-none fixed inset-x-0 bottom-3 z-50 flex justify-center px-4 sm:bottom-auto sm:top-3">
      <section
        aria-label="Estado del servidor"
        className="pointer-events-auto flex w-full max-w-md flex-col gap-3 rounded-2xl border border-border bg-card p-4 text-card-foreground shadow-lg motion-safe:animate-in motion-safe:fade-in-0"
      >
        <div className="flex items-start gap-3">
          <span
            aria-hidden="true"
            className={cn(
              "inline-flex size-9 shrink-0 items-center justify-center rounded-xl",
              state === "waking" && "bg-primary/10 text-primary",
              state === "stuck" && "bg-warning/15 text-warning",
              state === "offline" && "bg-muted text-muted-foreground",
            )}
          >
            <Icon className={cn("size-5", state === "waking" && "motion-safe:animate-pulse")} />
          </span>
          <div className="flex min-w-0 flex-col gap-0.5">
            <p role="status" aria-live="polite" className="font-semibold">
              {TITLES[state]}
            </p>
            <p className="text-sm text-muted-foreground">
              {state === "waking"
                ? "Esto puede tomar un minuto. No cierres esta pestaña."
                : state === "stuck"
                  ? "El servidor todavía no responde. Puedes seguir esperando o volver a intentarlo."
                  : "Revisa tu wifi o tus datos móviles."}
            </p>
          </div>
        </div>
        {state === "stuck" ? (
          <div className="flex flex-col gap-3">
            <Button size="sm" onClick={reload} className="self-start">
              Reintentar
            </Button>
            <SupportRow {...supportContact} lead="¿Sigue sin responder?" />
          </div>
        ) : null}
      </section>
    </div>
  );
}
