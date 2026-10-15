import { StaffManagement } from "@/modules/staff/components/StaffManagement";

/** Pantalla de gestión del personal (solo ADMIN). La feature vive en el módulo `staff`. */
export function StaffSettingsScreen() {
  return <StaffManagement />;
}
