import { Outlet } from "react-router-dom";
import { MotionConfig } from "framer-motion";
import { Toaster } from "sonner";
import { useSessionBootstrap } from "@/modules/auth/hooks/useSessionBootstrap";
import { useBranding } from "@/modules/branding/hooks/useBranding";
import { useElapsedWhile } from "@/modules/core/hooks/useElapsedWhile";
import { useOnlineStatus } from "@/modules/core/hooks/useOnlineStatus";
import { ServerWarmupScreen } from "@/modules/core/components/ServerWarmupScreen";

/**
 * Layout raíz: restaura la sesión al arrancar (splash mientras resuelve el
 * refresh), aplica la marca de la organización activa y monta el contenedor +
 * los toasts. Los guards deciden con el `status` ya resuelto.
 *
 * El refresh es la primera petición de toda carga: si el servidor está dormido (Render free), su
 * espera es la del arranque en frío. El splash la mide y, pasados unos segundos, la explica
 * (`ServerWarmupScreen`, add-cold-start-warmup D4/D5).
 */
export function RootLayout() {
  const status = useSessionBootstrap();
  useBranding();
  const waiting = status === "idle" || status === "loading";
  const elapsed = useElapsedWhile(waiting);
  const online = useOnlineStatus();

  if (waiting) {
    return <ServerWarmupScreen elapsedSeconds={elapsed} online={online} />;
  }

  return (
    // reducedMotion="user": respeta prefers-reduced-motion del SO para TODA animación de
    // framer-motion de la app, una sola vez aquí (docs/frontend.md §4.2).
    <MotionConfig reducedMotion="user">
      <div className="min-h-screen bg-background text-foreground">
        <Outlet />
        <Toaster richColors position="top-right" />
      </div>
    </MotionConfig>
  );
}
