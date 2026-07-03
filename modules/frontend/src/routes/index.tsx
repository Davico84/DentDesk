import { createBrowserRouter } from "react-router-dom";
import { RootLayout } from "@/layouts/RootLayout";
import { HomeScreen } from "@/screens/HomeScreen";
import { LoginView } from "@/screens/auth/LoginView";
import { RegisterView } from "@/screens/auth/RegisterView";
import { RequireAuth } from "@/modules/core/auth/RequireAuth";
import { RequireGuest } from "@/modules/core/auth/RequireGuest";
import { PATHS } from "./paths";

// RootLayout (bootstrap de sesión + splash) envuelve dos zonas:
// - RequireGuest: rutas de auth (solo sin sesión)
// - RequireAuth: rutas privadas (requieren sesión; opcionalmente por rol)
export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      {
        element: <RequireGuest />,
        children: [
          { path: PATHS.LOGIN, element: <LoginView /> },
          { path: PATHS.REGISTER, element: <RegisterView /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [{ path: PATHS.ROOT, element: <HomeScreen /> }],
      },
    ],
  },
]);
