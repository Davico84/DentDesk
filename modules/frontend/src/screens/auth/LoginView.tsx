import { Link } from "react-router-dom";
import { AuthLayout } from "@/modules/auth/components/AuthLayout";
import { LoginForm } from "@/modules/auth/components/LoginForm";
import { PATHS } from "@/routes/paths";

/** Pantalla de inicio de sesión. */
export function LoginView() {
  return (
    <AuthLayout
      title="Iniciar sesión"
      description="Accede a tu panel de OdontoRisas"
      footer={
        <p className="text-sm text-muted-foreground">
          ¿No tienes cuenta?{" "}
          <Link to={PATHS.REGISTER} className="font-medium text-primary underline-offset-4 hover:underline">
            Crear cuenta
          </Link>
        </p>
      }
    >
      <LoginForm />
    </AuthLayout>
  );
}
