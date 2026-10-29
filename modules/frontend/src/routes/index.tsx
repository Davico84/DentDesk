import { createBrowserRouter, Navigate, type RouteObject } from "react-router-dom";
import { RootLayout } from "@/layouts/RootLayout";
import { AppLayout } from "@/layouts/AppLayout";
import { HomeScreen } from "@/screens/home/HomeScreen";
import { BrandingSettingsScreen } from "@/screens/settings/BrandingSettingsScreen";
import { StaffSettingsScreen } from "@/screens/settings/StaffSettingsScreen";
import { PatientListScreen } from "@/screens/patients/PatientListScreen";
import { PatientFormScreen } from "@/screens/patients/PatientFormScreen";
import { PatientDetailScreen } from "@/screens/patients/PatientDetailScreen";
import { ClinicalHistoryScreen } from "@/screens/clinical-history/ClinicalHistoryScreen";
import { DentalChartScreen } from "@/screens/dental-chart/DentalChartScreen";
import { ConsultationNotesScreen } from "@/screens/consultation-notes/ConsultationNotesScreen";
import { LoginScreen } from "@/screens/auth/LoginScreen";
import { ProfileScreen } from "@/screens/profile/ProfileScreen";
import { RegisterScreen } from "@/screens/auth/RegisterScreen";
import { RequireAuth } from "@/modules/core/auth/RequireAuth";
import { RequireGuest } from "@/modules/core/auth/RequireGuest";
import { RouteError } from "@/modules/core/components/RouteError";
import { sectionById, type SectionId } from "@/modules/core/config/sections";
import { PATHS, PROFILE_CREDENTIAL } from "./paths";

// RootLayout (bootstrap de sesión + splash) envuelve dos zonas:
// - RequireGuest: rutas de auth (solo sin sesión), sin shell
// - RequireAuth → AppLayout (barra + cabecera + el único <main>): rutas privadas, agrupadas por
//   sección. Los roles de cada grupo salen de `core/config/sections` — el mismo lugar del que el
//   menú deriva sus ítems, así no se desalinean (add-app-shell design D1/D2).
// errorElement en la raíz: captura errores de render y rutas no encontradas (404).

/** Rutas de una sección, detrás del guard con los roles que declara su configuración. */
function section(id: SectionId, children: RouteObject[]): RouteObject {
  return { element: <RequireAuth roles={sectionById(id).roles} />, children };
}

/** Las dos zonas bajo `RootLayout`. Exportadas para que los tests las monten en un router en memoria. */
export const appRoutes: RouteObject[] = [
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
      {
        element: <AppLayout />,
        children: [
          section("home", [{ path: PATHS.ROOT, element: <HomeScreen /> }]),
          section("patients", [
            { path: PATHS.PATIENTS, element: <PatientListScreen /> },
            { path: PATHS.PATIENT_NEW, element: <PatientFormScreen /> },
            { path: PATHS.PATIENT_DETAIL, element: <PatientDetailScreen /> },
            { path: PATHS.PATIENT_EDIT, element: <PatientFormScreen /> },
            { path: PATHS.CLINICAL_HISTORY, element: <ClinicalHistoryScreen /> },
            { path: PATHS.DENTAL_CHART, element: <DentalChartScreen /> },
            { path: PATHS.CONSULTATION_NOTES, element: <ConsultationNotesScreen /> },
          ]),
          section("branding", [{ path: PATHS.BRANDING, element: <BrandingSettingsScreen /> }]),
          section("staff", [{ path: PATHS.STAFF, element: <StaffSettingsScreen /> }]),
          // De la persona, no una sección de la clínica: cualquier autenticado, sin ítem en la barra
          // lateral (se llega desde el menú de la cuenta). update-authentication-my-profile D6.
          { path: PATHS.PROFILE, element: <ProfileScreen /> },
          // La credencial vivía suelta aquí: la ruta anterior lleva a su sección, sin romper enlaces.
          { path: PATHS.LEGACY_PROFESSIONAL_CREDENTIAL, element: <Navigate to={PROFILE_CREDENTIAL} replace /> },
        ],
      },
    ],
  },
];

export const router = createBrowserRouter([
  { element: <RootLayout />, errorElement: <RouteError />, children: appRoutes },
]);
