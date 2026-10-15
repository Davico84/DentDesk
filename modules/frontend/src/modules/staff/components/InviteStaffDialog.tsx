import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/modules/core/ui/dialog";
import { FormField } from "@/modules/core/ui/form-field";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";
import { useInviteStaff } from "../hooks/useStaffApi";
import { inviteStaffSchema, type InviteStaffValues } from "../schemas/staff";

/**
 * Invitar a una persona de la clínica. El rol no se elige: un administrador de clínica solo invita como
 * Personal (spec staff). La validación vive en el schema; un conflicto del servidor (ya es miembro, ya tiene una
 * invitación pendiente o vencida) se muestra aquí, junto al campo, y no en un toast que se pierde.
 */
export function InviteStaffDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  const inviteMutation = useInviteStaff();
  const { register, handleSubmit, reset, setError, formState: { errors } } = useForm<InviteStaffValues>({
    resolver: zodResolver(inviteStaffSchema),
    mode: "onTouched",
    defaultValues: { email: "" },
  });

  const close = (next: boolean) => {
    if (!next) {
      reset();
      inviteMutation.reset();
    }
    onOpenChange(next);
  };

  const submit = (values: InviteStaffValues) =>
    inviteMutation.mutate(
      { email: values.email, role: "MEMBER" },
      {
        onSuccess: () => close(false),
        onError: (error) => setError("email", { type: "server", message: getUserFriendlyError(error) }),
      },
    );

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-4">
          <DialogHeader>
            <DialogTitle>Invitar a alguien</DialogTitle>
            <DialogDescription>
              Le llegará un correo con un enlace y un código para crear su cuenta. Entrará como Personal.
            </DialogDescription>
          </DialogHeader>
          <FormField
            label="Correo electrónico"
            name="email"
            type="email"
            inputMode="email"
            placeholder="nombre@correo.com"
            register={register}
            error={errors.email}
            required
          />
          <DialogFooter className="gap-2 sm:gap-2">
            <Button type="button" variant="ghost" onClick={() => close(false)} disabled={inviteMutation.isPending}>
              Cancelar
            </Button>
            <Button type="submit" disabled={inviteMutation.isPending}>
              {inviteMutation.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
              Enviar invitación
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
