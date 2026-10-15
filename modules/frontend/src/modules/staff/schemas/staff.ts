import { z } from "zod";

/** Paridad con `InvitationRequest` del backend (`@NotBlank @Email`). El rol no se elige: siempre MEMBER. */
export const inviteStaffSchema = z.object({
  email: z.string().trim().min(1, "El correo es obligatorio").check(z.email("Correo electrónico no válido")),
});

export type InviteStaffValues = z.infer<typeof inviteStaffSchema>;
