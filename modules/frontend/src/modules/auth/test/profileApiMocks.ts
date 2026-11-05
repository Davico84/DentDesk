import { http, HttpResponse } from "msw";
import type {
  AuthResponse,
  ChangePasswordRequest,
  ProfileRequest,
  ProfileResponse,
} from "@/modules/core/services/generated/model";

export const ANA_PROFILE: ProfileResponse = { fullName: "Ana Torres", email: "ana@clinica.test" };

/** Lo que el servidor devuelve al cambiar la contraseña: un access nuevo para la sesión actual. */
export const AFTER_PASSWORD_CHANGE: AuthResponse = {
  accessToken: "access-nuevo",
  user: {
    id: 1,
    email: "ana@clinica.test",
    fullName: "Ana Torres",
    role: "MEMBER",
    organizationId: 5,
    organizationName: "Clínica Sonrisas",
    organizationSlug: "clinica-sonrisas",
  },
};

const PASSWORD_PROBLEMS: Record<number, { type: string; detail: string }> = {
  400: { type: "/errors/current-password-incorrect", detail: "La contraseña actual no es correcta." },
  429: {
    type: "/errors/account-temporarily-locked",
    detail: "La cuenta está bloqueada temporalmente por intentos fallidos. Vuelve a intentarlo más tarde.",
  },
};

/**
 * Mocks de `/me/profile` y `/auth/password`. `passwordStatus` distinto de 200 responde el problema del servidor (400
 * contraseña actual incorrecta, 429 cuenta bloqueada). Registra lo enviado para afirmar qué salió y qué no.
 */
export function profileHandlers({
  profile = ANA_PROFILE,
  passwordStatus = 200,
}: { profile?: ProfileResponse; passwordStatus?: number } = {}) {
  const calls: { patches: ProfileRequest[]; passwords: ChangePasswordRequest[] } = { patches: [], passwords: [] };
  let state = profile;
  const handlers = [
    http.get("*/me/profile", () => HttpResponse.json(state)),
    http.patch("*/me/profile", async ({ request }) => {
      const body = (await request.json()) as ProfileRequest;
      calls.patches.push(body);
      state = { ...state, fullName: body.fullName };
      return HttpResponse.json(state);
    }),
    http.post("*/auth/password", async ({ request }) => {
      calls.passwords.push((await request.json()) as ChangePasswordRequest);
      const problem = PASSWORD_PROBLEMS[passwordStatus];
      if (problem) {
        return HttpResponse.json({ status: passwordStatus, ...problem }, { status: passwordStatus });
      }
      return HttpResponse.json(AFTER_PASSWORD_CHANGE);
    }),
  ];
  return { handlers, calls };
}
