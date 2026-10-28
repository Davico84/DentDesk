import { z } from "zod";
import { newPasswordSchema } from "./auth";

const NAME_LENGTH = "El nombre debe tener entre 2 y 120 caracteres";

/**
 * Corregir el nombre propio (update-authentication-my-profile D5). Paridad con `ProfileRequest`: se recorta antes de
 * medir, así que 2–120 cuenta sin los espacios de los extremos, que tampoco se envían.
 */
export const profileSchema = z.object({
  fullName: z.string().trim().min(2, NAME_LENGTH).max(120, NAME_LENGTH),
});

/**
 * Cambiar la contraseña propia. La nueva sigue la regla del registro; la confirmación solo existe en el cliente, como
 * en el registro. Que sea distinta de la actual también lo decide el servidor (es quien conoce el hash).
 */
export const changePasswordSchema = z
  .object({
    currentPassword: z.string().min(1, "Ingresa tu contraseña actual"),
    newPassword: newPasswordSchema,
    confirmPassword: z.string().min(1, "Confirma tu contraseña nueva"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Las contraseñas no coinciden",
    path: ["confirmPassword"],
  })
  .refine((data) => data.newPassword !== data.currentPassword, {
    message: "La contraseña nueva debe ser distinta de la actual",
    path: ["newPassword"],
  });

export type ProfileValues = z.infer<typeof profileSchema>;
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;
