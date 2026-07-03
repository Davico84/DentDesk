import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button } from "@/modules/core/ui/button";
import { Input } from "@/modules/core/ui/input";
import { Label } from "@/modules/core/ui/label";
import { registerSchema, type RegisterValues } from "../schemas/auth";
import { useRegister } from "../hooks/useAuth";

/** Formulario de registro. Crea la cuenta e inicia sesión; el RouteGuard redirige por rol. */
export function RegisterForm() {
  const {
    register: field,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: "", password: "" },
  });
  const registerMutation = useRegister();

  return (
    <form
      onSubmit={handleSubmit((values) => registerMutation.mutate(values))}
      className="flex flex-col gap-4"
      noValidate
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="reg-email">Correo electrónico</Label>
        <Input
          id="reg-email"
          type="email"
          autoComplete="email"
          aria-invalid={errors.email ? true : undefined}
          {...field("email")}
        />
        {errors.email ? <p className="text-sm text-destructive">{errors.email.message}</p> : null}
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
