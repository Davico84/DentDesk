import { Link } from "react-router-dom";
import { AuthLayout } from "@/modules/auth/components/AuthLayout";
import { InvitationRegistration } from "@/modules/auth/components/InvitationRegistration";
import { PATHS } from "@/routes/paths";

/** Pantalla de registro: solo existe para completar una invitación (`/auth/register?token=…`). */
export function RegisterScreen() {
  return (
    <AuthLayout
      title="Crear cuenta"
      description="Completa tu invitación a Dentdesk"
      footer={
        <p className="text-sm text-muted-foreground">
          ¿Ya tienes cuenta?{" "}
          <Link to={PATHS.LOGIN} className="font-medium text-primary underline-offset-4 hover:underline">
            Iniciar sesión
          </Link>
        </p>
      }
    >
      <InvitationRegistration />
    </AuthLayout>
  );
}
