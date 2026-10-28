import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound, Loader2 } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormSection, PasswordField } from "@/modules/core/ui/form-field";
import { applyServerFieldErrors, getApiProblem, getUserFriendlyError } from "@/modules/core/utils/apiError";
import { useChangePassword } from "../hooks/useProfile";
import { changePasswordSchema, type ChangePasswordValues } from "../schemas/profile";

const CURRENT_INCORRECT = "/errors/current-password-incorrect";
const ACCOUNT_LOCKED = "/errors/account-temporarily-locked";
const SAME_AS_CURRENT = "/errors/new-password-same-as-current";

const EMPTY: ChangePasswordValues = { currentPassword: "", newPassword: "", confirmPassword: "" };

/**
 * Cambiar la contraseña propia (spec authentication: Cambiar la contraseña propia · Cerrar las otras sesiones al
 * cambiar la contraseña). Los rechazos del servidor se pintan junto al campo que corresponde: la actual incorrecta y
 * la cuenta bloqueada en "Contraseña actual", la nueva igual a la actual en "Contraseña nueva".
 */
export function ChangePasswordForm() {
  const changeMutation = useChangePassword();
  const { register, handleSubmit, setError, reset, formState: { errors } } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    mode: "onTouched",
    defaultValues: EMPTY,
  });

  const submit = ({ currentPassword, newPassword }: ChangePasswordValues) =>
    changeMutation.mutate(
      { currentPassword, newPassword },
      {
        onSuccess: () => reset(EMPTY),
        onError: (error) => {
          const type = getApiProblem(error)?.type;
          const message = getUserFriendlyError(error);
          if (type === CURRENT_INCORRECT || type === ACCOUNT_LOCKED) {
            setError("currentPassword", { type: "server", message });
          } else if (type === SAME_AS_CURRENT) {
            setError("newPassword", { type: "server", message });
          } else if (!applyServerFieldErrors(error, setError)) {
            setError("root", { type: "server", message });
          }
        },
      },
    );

  return (
    <form onSubmit={handleSubmit(submit)} noValidate>
      <FormSection
        title="Contraseña"
        description="Al cambiarla se cierran tus sesiones en otros dispositivos. Esta sigue abierta."
        icon={<KeyRound className="size-4" />}
      >
        <PasswordField
          label="Contraseña actual"
          name="currentPassword"
          autoComplete="current-password"
          register={register}
          error={errors.currentPassword}
          required
          wide
        />
        <PasswordField
          label="Contraseña nueva"
          name="newPassword"
          autoComplete="new-password"
          placeholder="Mínimo 8 caracteres"
          register={register}
          error={errors.newPassword}
          required
        />
        <PasswordField
          label="Confirmar contraseña nueva"
          name="confirmPassword"
          autoComplete="new-password"
          placeholder="Repite la contraseña nueva"
          register={register}
          error={errors.confirmPassword}
          required
        />
        {errors.root ? (
          <p role="alert" className="text-sm text-destructive sm:col-span-2">
            {errors.root.message}
          </p>
        ) : null}
        <div className="flex justify-end sm:col-span-2">
          <Button type="submit" className="h-11 rounded-xl" disabled={changeMutation.isPending}>
            {changeMutation.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
            Cambiar contraseña
          </Button>
        </div>
      </FormSection>
    </form>
  );
}
