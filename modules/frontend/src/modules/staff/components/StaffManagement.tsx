import { useState } from "react";
import { Button } from "@/modules/core/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/modules/core/ui/card";
import { Input } from "@/modules/core/ui/input";
import { Label } from "@/modules/core/ui/label";
import { useInviteStaff, useMembersQuery } from "../hooks/useStaffApi";

export function StaffManagement() {
  const [email, setEmail] = useState("");
  const staff = useMembersQuery();
  const inviteMutation = useInviteStaff();

  function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    inviteMutation.mutate({ email, role: "MEMBER" }, { onSuccess: () => setEmail("") });
  }

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader><CardTitle>Invitar personal</CardTitle><CardDescription>Elige el rol que tendrá la persona en tu clínica.</CardDescription></CardHeader>
        <CardContent>
          <form onSubmit={submit} className="flex flex-col gap-4 sm:flex-row sm:items-end">
            <div className="flex flex-1 flex-col gap-1.5"><Label htmlFor="staff-email">Correo electrónico</Label><Input id="staff-email" type="email" required value={email} onChange={(event) => setEmail(event.target.value)} /></div>
            <Button type="submit" disabled={inviteMutation.isPending}>Crear invitación</Button>
          </form>
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>Personal de la clínica</CardTitle></CardHeader>
        <CardContent>
          {staff.isLoading ? <p>Cargando personal…</p> : staff.isError ? <p role="alert">No se pudo cargar el personal.</p> : staff.data?.length ? <div className="divide-y">{staff.data.map((member) => <div key={member.id} className="flex items-center justify-between py-3"><div><p className="font-medium">{member.fullName}</p><p className="text-sm text-muted-foreground">{member.email}</p></div><span className="text-sm text-muted-foreground">{member.role === "ADMIN" ? "Administrador" : "Personal"}</span></div>)}</div> : <p className="text-sm text-muted-foreground">Todavía no hay personal registrado.</p>}
        </CardContent>
      </Card>
    </div>
  );
}
