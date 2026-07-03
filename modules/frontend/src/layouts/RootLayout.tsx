import { Outlet } from "react-router-dom";
import { Toaster } from "sonner";

/** Layout raíz: contenedor de la app. Las screens se renderizan en el Outlet. */
export function RootLayout() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <Outlet />
      <Toaster richColors position="top-right" />
    </div>
  );
}
