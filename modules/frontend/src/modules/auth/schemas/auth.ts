import { z } from "zod";

/**
 * Schemas Zod de los formularios de auth. En paridad de **formato** con las
 * restricciones Bean Validation del backend (ver docs/coding-style.md §7).
 */
export const loginSchema = z.object({
  email: z.string().min(1, "El correo es obligatorio").email("Correo electrónico no válido"),
  password: z.string().min(1, "La contraseña es obligatoria"),
});

export const registerSchema = z.object({
  email: z.string().min(1, "El correo es obligatorio").email("Correo electrónico no válido"),
  password: z.string().min(8, "La contraseña debe tener al menos 8 caracteres"),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;
