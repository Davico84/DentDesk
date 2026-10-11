import { Mail, MessageCircle } from "lucide-react";
import { HintTooltip } from "@/modules/core/components/HintTooltip";
import { whatsappUrl } from "@/modules/core/config/supportContact";
import { cn } from "@/modules/core/utils/cn";

const ICON_BUTTON =
  "inline-flex size-11 shrink-0 items-center justify-center rounded-full transition-colors focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background";

/**
 * Contacto de soporte en una fila compacta (`add-support-contact` D2, opción C2 del diseño): la
 * pregunta y dos botones redondos de 44 px, WhatsApp y correo. Los botones son solo ícono: su nombre
 * va en `aria-label` y, a la vista, en un tooltip (sin `title`, `docs/frontend.md` §4.1). Componente
 * genérico de `core`: los datos llegan por props.
 */
export function SupportRow({
  whatsapp,
  email,
  product,
  lead = "¿Problemas para entrar?",
  className,
}: {
  whatsapp: string;
  email: string;
  product: string;
  /** La pregunta a la izquierda; cambia según dónde se muestra. */
  lead?: string;
  className?: string;
}) {
  return (
    <section
      aria-label="Soporte"
      className={cn(
        "flex items-center gap-2.5 rounded-full border border-border bg-muted/40 py-2 pl-4 pr-2",
        className,
      )}
    >
      <span className="flex-1 text-sm text-muted-foreground">{lead}</span>
      <HintTooltip content="WhatsApp" side="top" repeatsLabel>
        <a
          href={whatsappUrl(whatsapp, `Hola, necesito ayuda con ${product}`)}
          target="_blank"
          rel="noopener noreferrer"
          aria-label="Escribir a soporte por WhatsApp (se abre en otra pestaña)"
          className={cn(ICON_BUTTON, "bg-primary text-primary-foreground hover:bg-primary/90")}
        >
          <MessageCircle aria-hidden="true" className="size-[1.125rem]" />
        </a>
      </HintTooltip>
      <HintTooltip content={`Correo: ${email}`} side="top" repeatsLabel>
        <a
          href={`mailto:${email}`}
          aria-label={`Escribir a soporte por correo, ${email}`}
          className={cn(ICON_BUTTON, "border border-border bg-background text-foreground hover:bg-muted")}
        >
          <Mail aria-hidden="true" className="size-[1.125rem]" />
        </a>
      </HintTooltip>
    </section>
  );
}
