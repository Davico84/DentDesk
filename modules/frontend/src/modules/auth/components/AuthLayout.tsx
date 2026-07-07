import type { ReactNode } from "react";
import { ThemeToggle } from "@/modules/core/ui/theme-toggle";
import { BrandMark } from "./BrandMark";

interface AuthLayoutProps {
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
}

/**
 * Marco split-screen de las pantallas de auth: panel de marca (gradiente
 * teal→púrpura) a un lado y el formulario al otro. En móvil colapsa a una sola
 * columna con el logo arriba.
 */
export function AuthLayout({ title, description, children, footer }: AuthLayoutProps) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Panel de marca (solo desktop) */}
      <aside
        className="relative hidden flex-col justify-between p-12 text-white lg:flex"
        style={{ backgroundImage: "linear-gradient(150deg, hsl(174 70% 40%), hsl(292 72% 42%))" }}
      >
        <div className="flex items-center gap-3">
          <BrandMark className="size-9" />
          <span className="text-lg font-semibold tracking-tight">OdontoRisas</span>
        </div>
        <div className="max-w-sm">
          <h2 className="text-balance text-3xl font-semibold leading-tight">
            Sonrisas sanas, gestión simple.
          </h2>
          <p className="mt-3 text-white/80">
            La plataforma del Centro Odontológico OdontoRisas: pacientes, citas y tratamientos en un solo lugar.
          </p>
        </div>
        <p className="text-sm text-white/70">Centro Odontológico</p>
      </aside>

      {/* Panel del formulario */}
      <main className="relative flex items-center justify-center px-6 py-10">
        <div className="absolute right-4 top-4">
          <ThemeToggle />
        </div>
        <div className="w-full max-w-sm">
          <div className="mb-6 flex items-center gap-2 text-primary lg:hidden">
            <BrandMark className="size-8" />
            <span className="text-lg font-semibold tracking-tight text-foreground">OdontoRisas</span>
          </div>
          <div className="mb-6">
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            {description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
          </div>
          {children}
          {footer ? <div className="mt-6 text-center">{footer}</div> : null}
        </div>
      </main>
    </div>
  );
}
