import { Link } from "react-router-dom";
import { AuthCard } from "@/modules/auth/components/AuthCard";
import { RegisterForm } from "@/modules/auth/components/RegisterForm";
import { PATHS } from "@/routes/paths";

/** Pantalla de registro. */
export function RegisterView() {
  return (
    <AuthCard
      title="Crear cuenta"
      description="Regístrate para empezar a usar OdontoRisas"
      footer={
        <p className="text-sm text-muted-foreground">
          ¿Ya tienes cuenta?{" "}
          <Link to={PATHS.LOGIN} className="font-medium text-primary underline-offset-4 hover:underline">
            Iniciar sesión
          </Link>
        </p>
      }
    >
      <RegisterForm />
    </AuthCard>
  );
}
