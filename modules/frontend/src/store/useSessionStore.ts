import { create } from "zustand";
import type { UserResponse, UserResponseRole } from "@/modules/core/services/generated/model";
import { createSelectors } from "./createSelectors";

/** Rol de acceso: derivado del contrato (docs/frontend.md §2.3), no tecleado a mano. */
export type Role = UserResponseRole;

/** Usuario de la sesión: mismo shape que `UserResponse` — el login/refresh ya lo devuelven así. */
export type SessionUser = UserResponse;

/**
 * Estado de sesión. El access token vive **solo en memoria** (no se persiste):
 * la restauración tras recargar se hace vía el endpoint de refresh (cookie
 * HttpOnly). Ver docs/backend.md y la decisión D1 del change add-authentication.
 */
export type SessionStatus = "idle" | "loading" | "authenticated" | "unauthenticated";

interface SessionState {
  accessToken: string | null;
  user: SessionUser | null;
  status: SessionStatus;
  setSession: (accessToken: string, user: SessionUser) => void;
  setAccessToken: (accessToken: string) => void;
  setStatus: (status: SessionStatus) => void;
  clear: () => void;
}

const useSessionStoreBase = create<SessionState>((set) => ({
  accessToken: null,
  user: null,
  status: "idle",
  setSession: (accessToken, user) => set({ accessToken, user, status: "authenticated" }),
  setAccessToken: (accessToken) => set({ accessToken }),
  setStatus: (status) => set({ status }),
  clear: () => set({ accessToken: null, user: null, status: "unauthenticated" }),
}));

export const useSessionStore = createSelectors(useSessionStoreBase);
