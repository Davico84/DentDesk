import { Link, useSearchParams } from "react-router-dom";
import { AuthLayout } from "@/modules/auth/components/AuthLayout";
import { RegisterForm } from "@/modules/auth/components/RegisterForm";
import { PATHS } from "@/routes/paths";

/** Pantalla de registro. */
export function RegisterScreen() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") ?? "";
  return (
    <AuthLayout
      title="Crear cuenta"
      description="Regístrate para empezar a usar Dentdesk"
      footer={
        <p className="text-sm text-muted-foreground">
          ¿Ya tienes cuenta?{" "}
          <Link to={PATHS.LOGIN} className="font-medium text-primary underline-offset-4 hover:underline">
            Iniciar sesión
          </Link>
        </p>
      }
    >
      <RegisterForm token={token} />
    </AuthLayout>
  );
}
