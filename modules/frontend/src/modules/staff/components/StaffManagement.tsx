import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { MailPlus } from "lucide-react";
import { PageContainer } from "@/modules/core/components/PageContainer";
import { Button } from "@/modules/core/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/modules/core/ui/tabs";
import { InviteStaffDialog } from "./InviteStaffDialog";
import { InvitationsTab } from "./InvitationsTab";
import { MembersTab } from "./MembersTab";

const TAB_PARAM = "tab";
const INVITATIONS_TAB = "invitaciones";
const MEMBERS_TAB = "personal";

/**
 * Feature de gestión del personal (capacidad `staff`): pestañas Personal | Invitaciones y botón Invitar común.
 * La pestaña vive en la URL (`?tab=invitaciones`): se puede enlazar y sobrevive a una recarga (design D6).
 */
export function StaffManagement() {
  const [params, setParams] = useSearchParams();
  const [inviting, setInviting] = useState(false);
  const tab = params.get(TAB_PARAM) === INVITATIONS_TAB ? INVITATIONS_TAB : MEMBERS_TAB;

  const changeTab = (next: string) =>
    setParams(
      (prev) => {
        const updated = new URLSearchParams(prev);
        if (next === MEMBERS_TAB) updated.delete(TAB_PARAM);
        else updated.set(TAB_PARAM, next);
        return updated;
      },
      { replace: true },
    );

  return (
    <PageContainer>
      <header className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Personal</h1>
          <p className="mt-1 text-sm text-muted-foreground">Quién trabaja en tu clínica y las invitaciones que enviaste.</p>
        </div>
        <Button onClick={() => setInviting(true)}>
          <MailPlus className="mr-2 size-4" />
          Invitar
        </Button>
      </header>

      <Tabs value={tab} onValueChange={changeTab} className="flex flex-col gap-4">
        <TabsList className="h-11 self-start rounded-xl">
          <TabsTrigger value={MEMBERS_TAB} className="h-9 rounded-lg px-4">Personal</TabsTrigger>
          <TabsTrigger value={INVITATIONS_TAB} className="h-9 rounded-lg px-4">Invitaciones</TabsTrigger>
        </TabsList>
        <TabsContent value={MEMBERS_TAB} className="mt-0">
          <MembersTab />
        </TabsContent>
        <TabsContent value={INVITATIONS_TAB} className="mt-0">
          <InvitationsTab onInvite={() => setInviting(true)} />
        </TabsContent>
      </Tabs>

      <InviteStaffDialog open={inviting} onOpenChange={setInviting} />
    </PageContainer>
  );
}
