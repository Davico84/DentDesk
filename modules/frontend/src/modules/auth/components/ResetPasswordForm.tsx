import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import { Label } from "@/modules/core/ui/label";
import { PasswordInput } from "@/modules/core/ui/password-input";
import { cn } from "@/modules/core/utils/cn";
import { applyServerFieldErrors, getApiProblem, getUserFriendlyError } from "@/modules/core/utils/apiError";
import { PATHS } from "@/routes/paths";
import { resetPasswordSchema, type ResetPasswordValues } from "../schemas/auth";
import { useResetPassword } from "../hooks/usePasswordRecovery";
import { AuthSubmitButton } from "./AuthSubmitButton";
import { PasswordStrengthBar } from "./PasswordStrengthBar";
import type { PasswordResetNavigationState } from "./passwordResetDisplay";

const INVALID_LINK = "/errors/invalid-reset-link";

interface ResetPasswordFormProps {
  token: string;
  /** El enlace dejó de valer entre el check y el envío (usado en otra pestaña, vencido). */
  onInvalidLink: () => void;
}

/**
 * Elegir la contraseña nueva con un enlace vigente (spec authentication: Restablecer la contraseña con el enlace).
 * Al guardar, todas las sesiones se cierran y se vuelve al login con el aviso que corresponde.
 */
export function ResetPasswordForm({ token, onInvalidLink }: ResetPasswordFormProps) {
  const navigate = useNavigate();
  const resetMutation = useResetPassword();
  const { register, handleSubmit, control, setError, formState: { errors } } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    mode: "onTouched",
    defaultValues: { newPassword: "", confirmPassword: "" },
  });
  const newPassword = useWatch({ control, name: "newPassword" });

  const submit = (values: ResetPasswordValues) =>
    resetMutation.mutate(
      { token, newPassword: values.newPassword },
      {
        onSuccess: (result) => {
          const state: PasswordResetNavigationState = { passwordReset: result };
          navigate(PATHS.LOGIN, { replace: true, state });
        },
        onError: (error) => {
          if (getApiProblem(error)?.type === INVALID_LINK) {
            onInvalidLink();
          } else if (!applyServerFieldErrors(error, setError)) {
            setError("root", { type: "server", message: getUserFriendlyError(error) });
          }
        },
      },
    );

  return (
    <form onSubmit={handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reset-newPassword" className="text-sm font-medium">
          Contraseña nueva
        </Label>
        <div className="[&_input]:h-11 [&_input]:rounded-lg [&_input]:text-sm">
          <PasswordInput
            id="reset-newPassword"
            autoComplete="new-password"
            placeholder="Mínimo 8 caracteres"
            aria-invalid={errors.newPassword ? true : undefined}
            className={cn(errors.newPassword && "border-destructive focus-visible:ring-destructive")}
            {...register("newPassword")}
          />
        </div>
        <PasswordStrengthBar password={newPassword} />
        {errors.newPassword ? <p className="text-xs text-destructive">{errors.newPassword.message}</p> : null}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reset-confirmPassword" className="text-sm font-medium">
          Confirmar contraseña nueva
        </Label>
        <div className="[&_input]:h-11 [&_input]:rounded-lg [&_input]:text-sm">
          <PasswordInput
            id="reset-confirmPassword"
            autoComplete="new-password"
            placeholder="Repite la contraseña nueva"
            aria-invalid={errors.confirmPassword ? true : undefined}
            className={cn(errors.confirmPassword && "border-destructive focus-visible:ring-destructive")}
            {...register("confirmPassword")}
          />
        </div>
        {errors.confirmPassword ? <p className="text-xs text-destructive">{errors.confirmPassword.message}</p> : null}
      </div>

      {errors.root ? (
        <p role="alert" className="text-sm text-destructive">
          {errors.root.message}
        </p>
      ) : null}

      <p className="text-xs text-muted-foreground">
        Al guardarla se cierran tus sesiones en todos los dispositivos, también en este.
      </p>

      <AuthSubmitButton pending={resetMutation.isPending} pendingLabel="Guardando…">
        Guardar contraseña nueva
      </AuthSubmitButton>
    </form>
  );
}
