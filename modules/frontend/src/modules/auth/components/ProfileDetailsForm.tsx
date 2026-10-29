import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, UserRound } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormField, FormSection } from "@/modules/core/ui/form-field";
import type { ProfileResponse } from "@/modules/core/services/generated/model";
import { applyServerFieldErrors, getUserFriendlyError } from "@/modules/core/utils/apiError";
import { useUpdateProfile } from "../hooks/useProfile";
import { profileSchema, type ProfileValues } from "../schemas/profile";

/**
 * Datos personales (spec authentication: Ver el perfil propio · Corregir el nombre propio). El nombre se corrige; el
 * correo es la identidad de login y solo se muestra. Lo ya firmado conserva el nombre de entonces.
 */
export function ProfileDetailsForm({ profile }: { profile: ProfileResponse }) {
  const updateMutation = useUpdateProfile();
  const { register, handleSubmit, setError, reset, formState: { errors } } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    mode: "onTouched",
    defaultValues: { fullName: profile.fullName },
  });

  const submit = (values: ProfileValues) =>
    updateMutation.mutate(values, {
      onSuccess: (saved) => reset({ fullName: saved.fullName }),
      onError: (error) => {
        if (!applyServerFieldErrors(error, setError)) {
          setError("root", { type: "server", message: getUserFriendlyError(error) });
        }
      },
    });

  return (
    <form onSubmit={handleSubmit(submit)} noValidate>
      <FormSection
        title="Datos personales"
        description="Tu nombre aparece en lo que firmes desde ahora; lo ya firmado no cambia."
        icon={<UserRound className="size-4" />}
      >
        <FormField label="Nombre completo" name="fullName" register={register} error={errors.fullName} required />
        <div className="flex flex-col gap-1.5">
          <span id="profile-email-label" className="text-sm font-medium">
            Correo electrónico
          </span>
          <p
            aria-labelledby="profile-email-label"
            className="flex h-11 items-center rounded-xl border bg-muted/40 px-3 text-sm text-muted-foreground"
          >
            {profile.email}
          </p>
          <p className="text-xs text-muted-foreground">Es tu usuario para entrar: no se puede cambiar.</p>
        </div>
        {errors.root ? (
          <p role="alert" className="text-sm text-destructive sm:col-span-2">
            {errors.root.message}
          </p>
        ) : null}
        <div className="flex justify-end sm:col-span-2">
          <Button type="submit" className="h-11 rounded-xl" disabled={updateMutation.isPending}>
            {updateMutation.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
            Guardar nombre
          </Button>
        </div>
      </FormSection>
    </form>
  );
}
