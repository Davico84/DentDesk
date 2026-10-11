import type { ReactNode } from "react";
import { motion } from "framer-motion";
import { ThemeToggle } from "@/modules/core/ui/theme-toggle";
import { Logo } from "@/modules/core/ui/logo";
import { BrandPanel } from "@/modules/core/components/BrandPanel";
import { SupportDisclosure } from "@/modules/core/components/SupportDisclosure";
import { supportContact } from "@/modules/core/config/supportContact";

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
 * Marco split-screen de las pantallas de auth: panel de marca (`core/components/BrandPanel`,
 * color fijo de producto, no el de la clínica) y el formulario al otro lado. En móvil colapsa a
 * una sola columna con el logo arriba.
 */
export function AuthLayout({ title, description, children, footer }: AuthLayoutProps) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <BrandPanel />

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
          {/* Soporte para quien no puede entrar (add-support-contact D4): cerrado, a pedido. */}
          <motion.div {...riseIn(0.3)} className="mt-8">
            <SupportDisclosure {...supportContact} />
          </motion.div>
        </div>
      </main>
    </div>
  );
}
