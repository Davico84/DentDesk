import { http, HttpResponse } from "msw";
import type {
  ProfessionalCredentialRequest,
  ProfessionalCredentialResponse,
} from "@/modules/core/services/generated/model";

/** Sin credencial declarada: la capacidad dice por qué no se puede firmar. */
export const WITHOUT_CREDENTIAL: ProfessionalCredentialResponse = {
  clinicalCapacity: { state: "NO_CREDENTIAL", copNumber: null, licensedUntil: null, expiredOn: null },
};

export const ENABLED: ProfessionalCredentialResponse = {
  credential: { copNumber: "12345", licensedUntil: "2099-12-31", swornAt: "2026-10-26T15:00:00Z" },
  clinicalCapacity: { state: "ENABLED", copNumber: "12345", licensedUntil: "2099-12-31", expiredOn: null },
};

export const EXPIRED: ProfessionalCredentialResponse = {
  credential: { copNumber: "12345", licensedUntil: "2026-10-20", swornAt: "2026-01-01T15:00:00Z" },
  clinicalCapacity: { state: "EXPIRED", copNumber: "12345", licensedUntil: "2026-10-20", expiredOn: "2026-10-20" },
};

/**
 * Mocks de `/me/professional-credential`. `putStatus` distinto de 200 responde el problema del servidor que
 * corresponde (409 número de otra cuenta, 400 fecha pasada en la hora de Perú).
 */
export function credentialHandlers({
  current = WITHOUT_CREDENTIAL,
  putStatus = 200,
  putType,
}: { current?: ProfessionalCredentialResponse; putStatus?: number; putType?: string } = {}) {
  const calls: { puts: ProfessionalCredentialRequest[] } = { puts: [] };
  let state = current;
  const handlers = [
    http.get("*/me/professional-credential", () => HttpResponse.json(state)),
    http.put("*/me/professional-credential", async ({ request }) => {
      const body = (await request.json()) as ProfessionalCredentialRequest;
      calls.puts.push(body);
      if (putStatus !== 200) {
        const detail = putType === "/errors/credential-number-taken"
          ? "Ese número de colegiatura ya está registrado en otra cuenta."
          : "La fecha de habilitación debe ser hoy o posterior.";
        return HttpResponse.json({ status: putStatus, type: putType, detail }, { status: putStatus });
      }
      state = {
        credential: { copNumber: body.copNumber, licensedUntil: body.licensedUntil, swornAt: "2026-10-26T16:00:00Z" },
        clinicalCapacity: { state: "ENABLED", copNumber: body.copNumber, licensedUntil: body.licensedUntil, expiredOn: null },
      };
      return HttpResponse.json(state);
    }),
  ];
  return { handlers, calls };
}
