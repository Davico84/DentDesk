import type { ReactNode } from "react";
import { motion } from "framer-motion";
import { ThemeToggle } from "@/modules/core/ui/theme-toggle";
import { Logo } from "@/modules/core/ui/logo";
import { SmileArcBackground } from "./SmileArcBackground";
import { cn } from "@/modules/core/utils/cn";

interface AuthLayoutProps {
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
}

const EASE_STANDARD = [0.16, 1, 0.3, 1] as const;

function riseIn(delay: number) {
  return {
    initial: { opacity: 0, y: 10 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.5, delay, ease: EASE_STANDARD },
  };
}

/**
 * Marco split-screen de las pantallas de auth: panel de marca (color primario fijo, no
 * temático — las pantallas de auth conservan la identidad de producto) y el formulario al
 * otro lado. En móvil colapsa a una sola columna con el logo arriba.
 */
export function AuthLayout({ title, description, children, footer }: AuthLayoutProps) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Panel de marca: color FIJO (no sigue --primary), a propósito — es la identidad de
          producto aprobada, no un panel temático. Degradado radial monocromático, no el
          cian→magenta decorativo que DESIGN.md prohíbe (es profundidad tonal sobre un único
          color, no dos matices en pugna). En modo oscuro, un paso más profundo de la misma
          rampa tonal (no cambia de matiz) para que no choque con el resto de la UI oscura. */}
      <aside
        className={cn(
          "relative hidden flex-col justify-between overflow-hidden p-12 text-white lg:flex",
          "bg-[image:radial-gradient(120%_90%_at_12%_0%,#671a86_0%,#7f1aa6_45%,#481864_100%)]",
          "dark:bg-[image:radial-gradient(120%_90%_at_12%_0%,#481864_0%,#671a86_45%,#2b0e39_100%)]",
        )}
      >
        <SmileArcBackground />
        <motion.div {...riseIn(0.02)} className="relative mt-32 self-center">
          <Logo variant="dark" className="h-12 w-auto" />
        </motion.div>
        <motion.div {...riseIn(0.09)} className="relative max-w-md self-center text-center">
          <h2 className="text-balance text-3xl font-semibold leading-tight">
            Sonrisas sanas, gestión simple.
          </h2>
          <p className="mt-3 text-white/80">
            La plataforma para clínicas dentales: pacientes, citas y tratamientos en un solo lugar.
          </p>
        </motion.div>
        <motion.p {...riseIn(0.16)} className="relative self-center text-sm text-white/70">
          © Dentdesk
        </motion.p>
      </aside>

      {/* Panel del formulario */}
      <main className="relative flex items-center justify-center px-6 py-10">
        <div className="absolute right-4 top-4">
          <ThemeToggle />
        </div>
        <div className="w-full max-w-sm">
          <motion.div {...riseIn(0.02)} className="mb-8 flex justify-center lg:hidden">
            <Logo className="h-12 w-auto" />
          </motion.div>
          <motion.div {...riseIn(0.09)} className="mb-6">
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            {description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
          </motion.div>
          <motion.div {...riseIn(0.16)}>{children}</motion.div>
          {footer ? (
            <motion.div {...riseIn(0.23)} className="mt-6 text-center">
              {footer}
            </motion.div>
          ) : null}
        </div>
      </main>
    </div>
  );
}
