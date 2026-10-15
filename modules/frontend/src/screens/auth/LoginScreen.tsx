import { AuthLayout } from "@/modules/auth/components/AuthLayout";
import { LoginForm } from "@/modules/auth/components/LoginForm";

/** Pantalla de inicio de sesión. */
export function LoginScreen() {
  return (
    <AuthLayout
      title="Iniciar sesión"
      description="Accede a tu panel de Dentdesk"
      footer={
        <p className="text-sm text-muted-foreground">
          ¿No tienes cuenta? Pide una invitación al administrador de tu clínica.
        </p>
      }
    >
      <LoginForm />
    </AuthLayout>
  );
}
