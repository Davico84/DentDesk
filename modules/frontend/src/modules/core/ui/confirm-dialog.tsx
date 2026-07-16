import { Loader2 } from "lucide-react";
import { Button } from "./button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "./dialog";

/**
 * Confirmación antes de una acción con consecuencias. Primitivo genérico de `core/ui`: no conoce
 * ningún dominio — quien lo usa pone el texto y la acción.
 *
 * <p>Sobre el `Dialog` de shadcn/Radix, así que hereda gratis lo que importa: <b>foco atrapado</b>
 * dentro del diálogo, cierre con <kbd>Esc</kbd>, `aria-modal` y el resto del árbol marcado como
 * inerte para lectores de pantalla.
 *
 * <p>El texto debe decir <b>qué va a pasar</b>, no "¿estás seguro?": una confirmación que no
 * explica la consecuencia no informa, solo estorba.
 */
export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  cancelLabel = "Cancelar",
  onConfirm,
  destructive = false,
  pending = false,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel: string;
  cancelLabel?: string;
  onConfirm: () => void;
  /** Usa el color semántico de peligro para la acción (destructivo e irreversible). */
  destructive?: boolean;
  pending?: boolean;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        <DialogFooter className="gap-2 sm:gap-2">
          <Button variant="ghost" onClick={() => onOpenChange(false)} disabled={pending}>
            {cancelLabel}
          </Button>
          <Button variant={destructive ? "destructive" : "default"} onClick={onConfirm} disabled={pending}>
            {pending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
            {confirmLabel}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
