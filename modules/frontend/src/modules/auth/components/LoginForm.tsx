import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Mail } from "lucide-react";
import { Link } from "react-router-dom";
import { Label } from "@/modules/core/ui/label";
import { PasswordInput } from "@/modules/core/ui/password-input";
import { IconInput } from "@/modules/core/ui/icon-input";
import { loginSchema, type LoginValues } from "../schemas/auth";
import { useLogin } from "../hooks/useAuth";
import { AuthSubmitButton } from "./AuthSubmitButton";
import { cn } from "@/modules/core/utils/cn";
import { applyServerFieldErrors } from "@/modules/core/utils/apiError";
import { PATHS } from "@/routes/paths";
import { PasswordResetNotice } from "./PasswordResetNotice";

/** Formulario de inicio de sesión. La redirección por rol la maneja el RouteGuard. */
export function LoginForm() {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });
  const login = useLogin();

  return (
    <form
      onSubmit={handleSubmit((values) =>
        // El 400 del servidor trae `errors[{field, message}]`: se pintan junto al input, igual
        // que los de Zod. El 401 de credenciales no los trae, así que cae en el toast de useAuth.
        login.mutate(values, { onError: (error) => applyServerFieldErrors(error, setError) }),
      )}
      className="flex flex-col gap-5"
      noValidate
    >
      {/* Aviso tras restablecer la contraseña, si se llegó desde ahí. */}
      <PasswordResetNotice />

      {/* Campo correo */}
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="email" className="text-sm font-medium">
          Correo electrónico
        </Label>
        <IconInput
          id="email"
          type="email"
          autoComplete="email"
          placeholder="tu@correo.com"
          icon={<Mail className="size-4" />}
          hasError={!!errors.email}
          aria-invalid={errors.email ? true : undefined}
          {...register("email")}
        />
        {errors.email ? (
          <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-200">
            {errors.email.message}
          </p>
        ) : null}
      </div>

      {/* Campo contraseña */}
      <div className="flex flex-col gap-1.5">
        <div className="flex items-center justify-between">
          <Label htmlFor="password" className="text-sm font-medium">
            Contraseña
          </Label>
          <Link
            to={PATHS.FORGOT_PASSWORD}
            className="text-xs text-primary hover:underline underline-offset-4 transition-colors focus-visible:outline-hidden focus-visible:ring-1 focus-visible:ring-ring rounded"
          >
            ¿Olvidaste tu contraseña?
          </Link>
        </div>
        <div className="[&_input]:h-11 [&_input]:rounded-lg [&_input]:text-sm">
          <PasswordInput
            id="password"
            autoComplete="current-password"
            placeholder="••••••••"
            aria-invalid={errors.password ? true : undefined}
            className={cn(errors.password && "border-destructive focus-visible:ring-destructive")}
            {...register("password")}
          />
        </div>
        {errors.password ? (
          <p className="text-xs text-destructive animate-in fade-in slide-in-from-top-1 duration-200">
            {errors.password.message}
          </p>
        ) : null}
      </div>

      <AuthSubmitButton pending={login.isPending} pendingLabel="Iniciando sesión…" className="mt-1">
        Iniciar sesión
      </AuthSubmitButton>

      {/* Separador decorativo */}
      <div className="flex items-center gap-3 text-xs text-muted-foreground">
        <span className="h-px flex-1 bg-border" />
        <span>acceso seguro</span>
        <span className="h-px flex-1 bg-border" />
      </div>

      {/* Badges de seguridad */}
      <div className="flex items-center justify-center gap-4 text-[11px] text-muted-foreground">
        <span className="flex items-center gap-1">
          <span className="inline-block size-1.5 rounded-full bg-brand-green" />
          Conexión cifrada
        </span>
        <span className="flex items-center gap-1">
          <span className="inline-block size-1.5 rounded-full bg-brand-teal" />
          Datos protegidos
        </span>
      </div>
    </form>
  );
}
