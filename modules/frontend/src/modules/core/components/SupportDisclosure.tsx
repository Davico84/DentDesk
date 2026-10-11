import { useId, useState, type ComponentProps } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { CircleHelp } from "lucide-react";
import { SupportCard } from "./SupportCard";

/**
 * Botón discreto que despliega la tarjeta de soporte debajo, en el flujo (`add-support-contact` D3):
 * un acordeón de un solo panel, no un popover flotante, así no tapa el formulario en celular ni
 * necesita manejar foco o `Escape`. Cerrado al entrar. La animación respeta "reducir movimiento"
 * (`MotionConfig` del `RootLayout`).
 */
export function SupportDisclosure(props: Omit<ComponentProps<typeof SupportCard>, "id">) {
  const [open, setOpen] = useState(false);
  const cardId = useId();

  return (
    <div className="flex flex-col items-center gap-3">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={cardId}
        onClick={() => setOpen((o) => !o)}
        className="inline-flex min-h-11 items-center gap-2 rounded-full border border-border bg-muted/40 px-4 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring"
      >
        <CircleHelp aria-hidden="true" className="size-4" />
        ¿Problemas para entrar? Habla con soporte
      </button>
      <AnimatePresence initial={false}>
        {open ? (
          <motion.div
            key="card"
            className="w-full"
            initial={{ opacity: 0, y: -6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -6 }}
            transition={{ duration: 0.18 }}
          >
            <SupportCard id={cardId} {...props} className="text-left" />
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}
