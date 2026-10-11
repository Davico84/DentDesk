import { useEffect, useRef } from "react";
import { Clock3, Loader2, Sunrise, WifiOff } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { Logo } from "@/modules/core/ui/logo";
import { cn } from "@/modules/core/utils/cn";
import { ALMOST_AFTER_S, estimatedProgress, warmupState } from "@/modules/core/utils/serverWarmup";
import { supportContact } from "@/modules/core/config/supportContact";
import { BrandPanel } from "./BrandPanel";
import { SupportRow } from "./SupportRow";

const TITLES = {
  loading: "Cargando…",
  warming: "Preparando tu consultorio digital",
  stuck: "Está tardando más de lo normal",
  offline: "Sin conexión a internet",
} as const;

/**
 * Pantalla mientras la app espera su primera respuesta del servidor (`add-cold-start-warmup` D5).
 * En los primeros segundos, solo "Cargando…"; si la espera se alarga (el servidor se despierta tras
 * dormir), explica qué pasa, con el mismo panel de marca que el login. Componente genérico de
 * `core`: recibe los segundos de espera y si hay red; no sabe qué se espera.
 *
 * El lector de pantalla oye el título UNA vez por cambio de estado (región `status` propia), no
 * cada tic de la barra.
 */
export function ServerWarmupScreen({
  elapsedSeconds,
  online,
  reload = () => window.location.reload(),
}: {
  elapsedSeconds: number;
  online: boolean;
  /** Reinicia la app. Inyectable solo para los tests. */
  reload?: () => void;
}) {
  const state = warmupState(elapsedSeconds, online);
  const retryRef = useRef<HTMLButtonElement>(null);
  const wasOffline = useRef(!online);

  // Al volver la red, se reintenta solo.
  useEffect(() => {
    if (wasOffline.current && online) reload();
    wasOffline.current = !online;
  }, [online, reload]);

  // "Reintentar" recibe el foco al aparecer, para que se pueda usar sin buscarlo.
  useEffect(() => {
    if (state === "stuck") retryRef.current?.focus();
  }, [state]);

  const announcement = (
    <p role="status" aria-live="polite" className="sr-only">
      {TITLES[state]}
    </p>
  );

  if (state === "loading") {
    return (
      <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-background text-muted-foreground">
        <Logo className="h-10 w-auto" />
        <span className="flex items-center gap-2 text-sm">
          <Loader2 aria-hidden="true" className="size-4 animate-spin motion-reduce:animate-none" />
          Cargando…
        </span>
        {announcement}
      </div>
    );
  }

  const progress = estimatedProgress(elapsedSeconds);

  return (
    <div className="grid min-h-screen bg-background lg:grid-cols-2">
      <BrandPanel />
      <main className="flex items-center justify-center px-6 py-10">
        <div className="flex w-full max-w-sm flex-col gap-6">
          {/* El `lg:hidden` va en el contenedor: en el Logo chocaría con su `dark:block` interno. */}
          <div className="flex justify-center lg:hidden">
            <Logo className="h-10 w-auto" />
          </div>
          {announcement}

          <span
            aria-hidden="true"
            className={cn(
              "inline-flex size-14 items-center justify-center rounded-2xl",
              state === "warming" && "bg-primary/10 text-primary",
              state === "stuck" && "bg-warning/15 text-warning",
              state === "offline" && "bg-muted text-muted-foreground",
            )}
          >
            {state === "warming" ? (
              <Sunrise className="size-7 animate-pulse motion-reduce:animate-none" />
            ) : state === "stuck" ? (
              <Clock3 className="size-7" />
            ) : (
              <WifiOff className="size-7" />
            )}
          </span>

          <div className="flex flex-col gap-2">
            <h1 className="text-balance text-2xl font-semibold tracking-tight">{TITLES[state]}</h1>
            <p className="text-muted-foreground">
              {state === "warming"
                ? "Estamos preparando tu consultorio digital para iniciar el día, esto puede tomar un minuto…"
                : state === "stuck"
                  ? "El servidor todavía no responde. Puedes seguir esperando o volver a intentarlo."
                  : "Revisa tu wifi o tus datos móviles. Volveremos a intentarlo solos cuando regrese la conexión."}
            </p>
          </div>

          {state === "warming" ? (
            <div className="flex flex-col gap-3">
              <div
                role="progressbar"
                aria-label="Progreso estimado"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={Math.round(progress)}
                className="h-2 overflow-hidden rounded-full bg-muted"
              >
                <div
                  className="h-full origin-left rounded-full bg-primary transition-transform duration-700 ease-out motion-reduce:transition-none"
                  style={{ transform: `scaleX(${progress / 100})` }}
                />
              </div>
              <p className="text-sm text-muted-foreground">
                {elapsedSeconds >= ALMOST_AFTER_S
                  ? "Ya casi está…"
                  : "Esto suele tardar menos de un minuto. No cierres esta pestaña: continuará sola."}
              </p>
            </div>
          ) : null}

          {state === "stuck" ? (
            <div className="flex flex-col gap-3">
              <Button ref={retryRef} size="lg" onClick={reload} className="self-start">
                Reintentar
              </Button>
              {/* La espera larga es el caso más probable de "no puedo entrar": el contacto, a la vista. */}
              <SupportRow {...supportContact} lead="¿Sigue sin entrar?" className="mt-3" />
            </div>
          ) : null}

          {state === "offline" ? (
            <Button variant="outline" onClick={reload} className="self-start">
              Reintentar ahora
            </Button>
          ) : null}
        </div>
      </main>
    </div>
  );
}
