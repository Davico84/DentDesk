import { Mail, MessageCircle } from "lucide-react";
import { cn } from "@/modules/core/utils/cn";
import { whatsappUrl } from "@/modules/core/config/supportContact";
import { Monogram } from "./Monogram";

const ACTION =
  "flex min-h-11 flex-1 items-center justify-center gap-2 rounded-xl border border-border bg-background px-3 text-sm font-semibold transition-colors hover:bg-muted focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring";

/**
 * Tarjeta de soporte (`add-support-contact` D2): quién atiende, en qué horario, y dos botones para
 * escribirle. Una persona con horario, no una línea de enlaces: quien no puede entrar sabe a quién le
 * escribe y cuándo le responden. Componente genérico de `core`: los datos llegan por props.
 */
export function SupportCard({
  id,
  name,
  role,
  hours,
  whatsapp,
  email,
  product,
  lead = "¿Necesitas ayuda? Te atiende",
  className,
}: {
  id?: string;
  name: string;
  role: string;
  hours: string;
  whatsapp: string;
  email: string;
  product: string;
  /** La frase sobre el nombre; cambia según dónde se muestra la tarjeta. */
  lead?: string;
  className?: string;
}) {
  return (
    <section
      id={id}
      aria-label="Soporte"
      className={cn("flex flex-col gap-4 rounded-2xl border border-border bg-muted/40 p-4", className)}
    >
      <div className="flex items-center gap-3">
        <Monogram name={name} tone="secondary" className="size-11 rounded-full text-sm" />
        <div className="flex min-w-0 flex-col">
          <span className="text-sm text-muted-foreground">{lead}</span>
          <span className="font-semibold text-foreground">
            {name} · {role}
          </span>
          <span className="text-sm text-muted-foreground">Respondo {hours}</span>
        </div>
      </div>
      <div className="flex gap-2.5">
        <a
          href={whatsappUrl(whatsapp, `Hola, necesito ayuda con ${product}`)}
          target="_blank"
          rel="noopener noreferrer"
          className={cn(ACTION, "text-primary")}
        >
          <MessageCircle aria-hidden="true" className="size-4" />
          WhatsApp{" "}
          <span className="sr-only">(se abre en otra pestaña)</span>
        </a>
        <a href={`mailto:${email}`} className={cn(ACTION, "text-foreground")}>
          <Mail aria-hidden="true" className="size-4" />
          Correo{" "}
          <span className="sr-only">{email}</span>
        </a>
      </div>
    </section>
  );
}
