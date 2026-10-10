import { createBrowserRouter } from "react-router-dom";
import { RootLayout } from "@/layouts/RootLayout";
import { HomeScreen } from "@/screens/home/HomeScreen";
import { BrandingSettingsScreen } from "@/screens/settings/BrandingSettingsScreen";
import { PatientListScreen } from "@/screens/patients/PatientListScreen";
import { PatientFormScreen } from "@/screens/patients/PatientFormScreen";
import { PatientDetailScreen } from "@/screens/patients/PatientDetailScreen";
import { ClinicalHistoryScreen } from "@/screens/clinical-history/ClinicalHistoryScreen";
import { DentalChartScreen } from "@/screens/dental-chart/DentalChartScreen";
import { LoginScreen } from "@/screens/auth/LoginScreen";
import { RegisterScreen } from "@/screens/auth/RegisterScreen";
import { RequireAuth } from "@/modules/core/auth/RequireAuth";
import { RequireGuest } from "@/modules/core/auth/RequireGuest";
import { RouteError } from "@/modules/core/components/RouteError";
import { PATHS } from "./paths";

// RootLayout (bootstrap de sesión + splash) envuelve dos zonas:
// - RequireGuest: rutas de auth (solo sin sesión)
// - RequireAuth: rutas privadas (requieren sesión; opcionalmente por rol)
// errorElement en la raíz: captura errores de render y rutas no encontradas (404).
export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    errorElement: <RouteError />,
    children: [
      {
        element: <RequireGuest />,
        children: [
          { path: PATHS.LOGIN, element: <LoginScreen /> },
          { path: PATHS.REGISTER, element: <RegisterScreen /> },
        ],
      },
      {
        element: <RequireAuth />,
        children: [
          { path: PATHS.ROOT, element: <HomeScreen /> },
          // Pacientes: los tres roles los gestionan; basta con estar autenticado (sin rol).
          { path: PATHS.PATIENTS, element: <PatientListScreen /> },
          { path: PATHS.PATIENT_NEW, element: <PatientFormScreen /> },
          { path: PATHS.PATIENT_DETAIL, element: <PatientDetailScreen /> },
          { path: PATHS.PATIENT_EDIT, element: <PatientFormScreen /> },
          { path: PATHS.CLINICAL_HISTORY, element: <ClinicalHistoryScreen /> },
          { path: PATHS.DENTAL_CHART, element: <DentalChartScreen /> },
        ],
      },
      {
        element: <RequireAuth roles={["ADMIN"]} />,
        children: [{ path: PATHS.BRANDING, element: <BrandingSettingsScreen /> }],
      },
    ],
  },
]);
