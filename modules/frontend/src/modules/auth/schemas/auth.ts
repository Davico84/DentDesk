import { z } from "zod";

/**
 * Schemas Zod de los formularios de auth. En paridad de **formato** con las
 * restricciones Bean Validation del backend (ver docs/coding-style.md §7).
 */
export const loginSchema = z.object({
  email: z.string().min(1, "El correo es obligatorio").check(z.email("Correo electrónico no válido")),
  password: z.string().min(1, "La contraseña es obligatoria"),
});

export const registerSchema = z
  .object({
    organizationName: z.string().min(2, "Ingresa el nombre de la clínica").max(120),
    fullName: z.string().min(2, "Ingresa tu nombre completo").max(120),
    email: z.string().min(1, "El correo es obligatorio").check(z.email("Correo electrónico no válido")),
    password: z.string().min(8, "La contraseña debe tener al menos 8 caracteres"),
    confirmPassword: z.string().min(1, "Confirma tu contraseña"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Las contraseñas no coinciden",
    path: ["confirmPassword"],
  });

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;
