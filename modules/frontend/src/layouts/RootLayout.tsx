import { Outlet } from "react-router-dom";
import { MotionConfig } from "framer-motion";
import { Toaster } from "sonner";
import { useSessionBootstrap } from "@/modules/auth/hooks/useSessionBootstrap";
import { useBranding } from "@/modules/branding/hooks/useBranding";

/**
 * Layout raíz: restaura la sesión al arrancar (splash mientras resuelve el
 * refresh), aplica la marca de la organización activa y monta el contenedor +
 * los toasts. Los guards deciden con el `status` ya resuelto.
 */
export function RootLayout() {
  const status = useSessionBootstrap();
  useBranding();

  if (status === "idle" || status === "loading") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background text-muted-foreground">
        Cargando…
      </div>
    );
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
