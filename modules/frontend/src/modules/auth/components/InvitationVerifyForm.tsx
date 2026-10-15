import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Mail, ShieldCheck } from "lucide-react";
import { Label } from "@/modules/core/ui/label";
import { IconInput } from "@/modules/core/ui/icon-input";
import { getUserFriendlyError } from "@/modules/core/utils/apiError";
import { invitationVerificationSchema, type InvitationVerificationValues } from "../schemas/auth";
import { useVerifyInvitation } from "../hooks/useAuth";
import { AuthSubmitButton } from "./AuthSubmitButton";

/**
 * Paso 1 del alta: el código de 6 dígitos que llegó al correo invitado. El correo es el de la
 * invitación (solo lectura): verificar otro no tendría sentido, el backend lo rechazaría.
 */
export function InvitationVerifyForm({ token, email, onVerified }: {
  token: string;
  email: string;
  onVerified: (email: string) => void;
}) {
  const { register, handleSubmit, formState: { errors } } = useForm<InvitationVerificationValues>({
    resolver: zodResolver(invitationVerificationSchema),
    mode: "onTouched",
    defaultValues: { email, code: "" },
  });
  const verifyMutation = useVerifyInvitation();

  return (
    <form onSubmit={handleSubmit((values) => verifyMutation.mutate(
      { token, email: values.email, code: values.code },
      { onSuccess: () => onVerified(values.email) },
    ))} className="flex flex-col gap-4" noValidate>
      <p className="text-sm text-muted-foreground">Ingresa el código de 6 dígitos que enviamos a tu correo.</p>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="verify-email">Correo electrónico</Label>
        <IconInput id="verify-email" type="email" autoComplete="email" readOnly icon={<Mail className="size-4" />}
          {...register("email")} />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="verify-code">Código de verificación</Label>
        <IconInput id="verify-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
          placeholder="123456" icon={<ShieldCheck className="size-4" />} hasError={!!errors.code}
          aria-invalid={errors.code ? true : undefined} {...register("code")} />
        {errors.code ? <p className="text-xs text-destructive">{errors.code.message}</p> : null}
      </div>
      {verifyMutation.isError
        ? <p role="alert" className="text-xs text-destructive">{getUserFriendlyError(verifyMutation.error)}</p>
        : null}
      <AuthSubmitButton pending={verifyMutation.isPending} pendingLabel="Verificando…">Verificar correo</AuthSubmitButton>
    </form>
  );
}
