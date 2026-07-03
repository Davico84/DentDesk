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
  fullName: z.string().min(2, "Ingresa tu nombre completo").max(120),
  username: z
    .string()
    .min(3, "El usuario debe tener al menos 3 caracteres")
    .max(30, "Máximo 30 caracteres")
    .regex(/^[a-zA-Z0-9._-]+$/, "Solo letras, números y . _ -"),
  email: z.string().min(1, "El correo es obligatorio").email("Correo electrónico no válido"),
  phone: z
    .string()
    .min(1, "El teléfono es obligatorio")
    .regex(/^[+0-9 ()-]{7,20}$/, "Teléfono no válido"),
  country: z.string().min(1, "El país es obligatorio").max(56),
  password: z.string().min(8, "La contraseña debe tener al menos 8 caracteres"),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;
