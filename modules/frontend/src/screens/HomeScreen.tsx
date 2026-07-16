import { Link } from "react-router-dom";
import { useSessionStore } from "@/store/useSessionStore";
import { useLogout } from "@/modules/auth/hooks/useAuth";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { OrgLogo } from "@/modules/branding/components/OrgLogo";
import { PATHS } from "@/routes/paths";

/** Panel raíz (provisional): muestra la marca de la organización, saluda y cierra sesión. */
export function HomeScreen() {
  const user = useSessionStore((s) => s.user);
  const logout = useLogout();

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4">
      <OrgLogo className="h-12 w-auto" />
      <h1 className="text-2xl font-bold text-primary">{user?.organizationName ?? "OdontoRisas"}</h1>
      {user ? (
        <p className="text-muted-foreground">
          Sesión iniciada como{" "}
          <span className="font-medium text-foreground">{user.fullName ?? user.email}</span> ({user.role})
        </p>
      ) : null}
      <div className="flex items-center gap-3">
        <Link to={PATHS.PATIENTS} className={buttonVariants({ variant: "default" })}>
          Pacientes
        </Link>
        {user?.role === "ADMIN" ? (
          <Link to={PATHS.BRANDING} className={buttonVariants({ variant: "outline" })}>
            Personalizar marca
          </Link>
        ) : null}
        <Button variant="outline" onClick={() => logout.mutate()} disabled={logout.isPending}>
          {logout.isPending ? "Cerrando sesión…" : "Cerrar sesión"}
        </Button>
      </div>
    </main>
  );
}
