import { Link } from "react-router-dom";
import { AuthLayout } from "@/modules/auth/components/AuthLayout";
import { ForgotPasswordForm } from "@/modules/auth/components/ForgotPasswordForm";
import { PATHS } from "@/routes/paths";

/** Pantalla de pedido de recuperación de la contraseña. */
export function ForgotPasswordScreen() {
  return (
    <AuthLayout
      title="Recuperar contraseña"
      description="Te enviaremos un enlace para elegir una contraseña nueva"
      footer={
        <Link to={PATHS.LOGIN} className="text-sm text-primary hover:underline underline-offset-4">
          Volver a iniciar sesión
        </Link>
      }
    >
      <ForgotPasswordForm />
    </AuthLayout>
  );
}
