import { z } from "zod";

/**
 * Regla de la contraseña nueva, la misma en el registro y al cambiarla (paridad con el `@Size(min = 8)` de
 * `RegisterRequest` y `ChangePasswordRequest`). Se comparte para que no diverjan.
 */
export const newPasswordSchema = z.string().min(8, "La contraseña debe tener al menos 8 caracteres");

export const loginSchema = z.object({
  email: z.string().min(1, "El correo es obligatorio").check(z.email("Correo electrónico no válido")),
  password: z.string().min(1, "La contraseña es obligatoria"),
});

export const registerSchema = z
  .object({
    token: z.string().min(1, "El enlace de invitación es obligatorio"),
    fullName: z.string().min(2, "Ingresa tu nombre completo").max(120),
    email: z.string().min(1, "El correo es obligatorio").check(z.email("Correo electrónico no válido")),
    password: newPasswordSchema,
    confirmPassword: z.string().min(1, "Confirma tu contraseña"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Las contraseñas no coinciden",
    path: ["confirmPassword"],
  });

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;

export const invitationVerificationSchema = z.object({
  email: z.string().min(1, "El correo es obligatorio").check(z.email("Correo electrónico no válido")),
  code: z.string().regex(/^\d{6}$/, "Ingresa el código de 6 dígitos"),
});

export type InvitationVerificationValues = z.infer<typeof invitationVerificationSchema>;
