import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Mail } from "lucide-react";
import { Label } from "@/modules/core/ui/label";
import { IconInput } from "@/modules/core/ui/icon-input";
import { applyServerFieldErrors, getUserFriendlyError } from "@/modules/core/utils/apiError";
import { passwordRecoverySchema, type PasswordRecoveryValues } from "../schemas/auth";
import { useRequestPasswordRecovery } from "../hooks/usePasswordRecovery";
import { AuthSubmitButton } from "./AuthSubmitButton";
import { PasswordRecoverySent } from "./PasswordRecoverySent";

/**
 * Pedir la recuperación de la contraseña (spec authentication: Pedir la recuperación de la contraseña). Tras enviar
 * muestra siempre el mismo mensaje, exista o no la cuenta: el servidor tampoco lo distingue.
 */
export function ForgotPasswordForm() {
  const recovery = useRequestPasswordRecovery();
  const { register, handleSubmit, setError, formState: { errors } } = useForm<PasswordRecoveryValues>({
    resolver: zodResolver(passwordRecoverySchema),
    mode: "onTouched",
    defaultValues: { email: "" },
  });

  if (recovery.isSuccess) {
    return <PasswordRecoverySent />;
  }

  return (
    <form
      onSubmit={handleSubmit((values) =>
        recovery.mutate(values, {
          onError: (error) => {
            if (!applyServerFieldErrors(error, setError)) {
              setError("root", { type: "server", message: getUserFriendlyError(error) });
            }
          },
        }),
      )}
      className="flex flex-col gap-5"
      noValidate
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="recovery-email" className="text-sm font-medium">
          Correo electrónico
        </Label>
        <IconInput
          id="recovery-email"
          type="email"
          autoComplete="email"
          placeholder="tu@correo.com"
          icon={<Mail className="size-4" />}
          hasError={!!errors.email}
          aria-invalid={errors.email ? true : undefined}
          {...register("email")}
        />
        {errors.email ? <p className="text-xs text-destructive">{errors.email.message}</p> : null}
      </div>

      {errors.root ? (
        <p role="alert" className="text-sm text-destructive">
          {errors.root.message}
        </p>
      ) : null}

      <AuthSubmitButton pending={recovery.isPending} pendingLabel="Enviando…">
        Enviar enlace
      </AuthSubmitButton>
    </form>
  );
}
