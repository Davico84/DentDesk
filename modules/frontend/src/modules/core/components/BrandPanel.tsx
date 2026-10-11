import { motion } from "framer-motion";
import { Logo } from "@/modules/core/ui/logo";
import { cn } from "@/modules/core/utils/cn";
import { SmileArcBackground } from "./SmileArcBackground";

const EASE_STANDARD = [0.16, 1, 0.3, 1] as const;

function riseIn(delay: number) {
  return {
    initial: { opacity: 0, y: 10 },
    animate: { opacity: 1, y: 0 },
    transition: { duration: 0.5, delay, ease: EASE_STANDARD },
  };
}

/**
 * Panel de marca del producto (escritorio, ≥ 1024 px): logo, frase y arcos de fondo. Lo comparten
 * el login, el registro y la pantalla de arranque en frío, para que pasar de una a otra no salte
 * (`add-cold-start-warmup` D5). Color FIJO de producto, no el de la clínica: tokens
 * `--brand-hero-*` (`update-tailwind-v4` D5), que cambian con el modo claro/oscuro.
 */
export function BrandPanel() {
  return (
    // Degradado radial monocromático: profundidad tonal sobre un único color, no dos matices en
    // pugna. En modo oscuro, un paso más profundo de la misma rampa para no chocar con la UI oscura.
    <aside
      className={cn(
        "relative hidden flex-col justify-between overflow-hidden p-12 text-white lg:flex",
        "bg-[radial-gradient(120%_90%_at_12%_0%,var(--brand-hero-from)_0%,var(--brand-hero-via)_45%,var(--brand-hero-to)_100%)]",
      )}
    >
      <SmileArcBackground />
      <motion.div {...riseIn(0.02)} className="relative mt-32 self-center">
        <Logo variant="dark" className="h-12 w-auto" />
      </motion.div>
      <motion.div {...riseIn(0.09)} className="relative max-w-md self-center text-center">
        <h2 className="text-balance text-3xl font-semibold leading-tight">Sonrisas sanas, gestión simple.</h2>
        <p className="mt-3 text-white/80">
          La plataforma para clínicas dentales: pacientes, citas y tratamientos en un solo lugar.
        </p>
      </motion.div>
      <motion.p {...riseIn(0.16)} className="relative self-center text-sm text-white/70">
        © Dentdesk
      </motion.p>
    </aside>
  );
}
