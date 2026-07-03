import { createBrowserRouter } from "react-router-dom";
import { RootLayout } from "@/layouts/RootLayout";
import { HomeScreen } from "@/screens/HomeScreen";
import { PATHS } from "./paths";

// Definición del enrutador. Las capacidades añadirán sus rutas (protegidas por RouteGuard).
export const router = createBrowserRouter([
  {
    path: PATHS.ROOT,
    element: <RootLayout />,
    children: [{ index: true, element: <HomeScreen /> }],
  },
]);
