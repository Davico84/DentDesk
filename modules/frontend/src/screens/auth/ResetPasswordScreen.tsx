import { AuthLayout } from "@/modules/auth/components/AuthLayout";
import { PasswordReset } from "@/modules/auth/components/PasswordReset";

/** Pantalla para elegir la contraseña nueva desde el enlace del correo. */
export function ResetPasswordScreen() {
  return (
    <AuthLayout title="Nueva contraseña" description="Elige la contraseña con la que vas a entrar">
      <PasswordReset />
    </AuthLayout>
  );
}
