import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/modules/core/ui/button";
import { Input } from "@/modules/core/ui/input";
import { Label } from "@/modules/core/ui/label";
import { registerSchema, type RegisterValues } from "../schemas/auth";
import { useRegister } from "../hooks/useAuth";

/** Formulario de registro con datos básicos de perfil. El RouteGuard redirige por rol al éxito. */
export function RegisterForm() {
  const {
    register: field,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { fullName: "", username: "", email: "", phone: "", country: "", password: "" },
  });
  const registerMutation = useRegister();

  return (
    <form
      onSubmit={handleSubmit((values) => registerMutation.mutate(values))}
      className="flex flex-col gap-4"
      noValidate
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reg-fullName">Nombre completo</Label>
        <Input id="reg-fullName" autoComplete="name" aria-invalid={errors.fullName ? true : undefined} {...field("fullName")} />
        {errors.fullName ? <p className="text-sm text-destructive">{errors.fullName.message}</p> : null}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reg-username">Nombre de usuario</Label>
        <Input id="reg-username" autoComplete="username" aria-invalid={errors.username ? true : undefined} {...field("username")} />
        {errors.username ? <p className="text-sm text-destructive">{errors.username.message}</p> : null}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reg-email">Correo electrónico</Label>
        <Input id="reg-email" type="email" autoComplete="email" aria-invalid={errors.email ? true : undefined} {...field("email")} />
        {errors.email ? <p className="text-sm text-destructive">{errors.email.message}</p> : null}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reg-phone">Teléfono</Label>
        <Input id="reg-phone" type="tel" autoComplete="tel" aria-invalid={errors.phone ? true : undefined} {...field("phone")} />
        {errors.phone ? <p className="text-sm text-destructive">{errors.phone.message}</p> : null}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reg-country">País</Label>
        <Input id="reg-country" autoComplete="country-name" aria-invalid={errors.country ? true : undefined} {...field("country")} />
        {errors.country ? <p className="text-sm text-destructive">{errors.country.message}</p> : null}
      </div>

      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reg-password">Contraseña</Label>
        <Input
          id="reg-password"
          type="password"
          autoComplete="new-password"
          aria-invalid={errors.password ? true : undefined}
          {...field("password")}
        />
        {errors.password ? (
          <p className="text-sm text-destructive">{errors.password.message}</p>
        ) : (
          <p className="text-sm text-muted-foreground">Mínimo 8 caracteres.</p>
        )}
      </div>

      <Button type="submit" disabled={registerMutation.isPending}>
        {registerMutation.isPending ? "Creando cuenta…" : "Crear cuenta"}
      </Button>
    </form>
  );
}
