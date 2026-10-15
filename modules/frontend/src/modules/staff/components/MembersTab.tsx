import { useState } from "react";
import { AnimatePresence } from "framer-motion";
import { Search } from "lucide-react";
import { ConfirmDialog } from "@/modules/core/ui/confirm-dialog";
import { Input } from "@/modules/core/ui/input";
import type { MemberResponse } from "@/modules/core/services/generated/model";
import { useSessionStore } from "@/store/useSessionStore";
import { useChangeMemberStatus, useMembersQuery } from "../hooks/useStaffApi";
import { filterMembers } from "../utils/staffFilters";
import { MemberRow } from "./MemberRow";
import { MembersSearchEmpty } from "./MembersSearchEmpty";
import { StaffListError } from "./StaffListError";
import { StaffListSkeleton } from "./StaffListSkeleton";

/**
 * Pestaña Personal: miembros con rol y estado, búsqueda por nombre o correo (sin tildes, en el cliente:
 * design D5) y desactivar/reactivar con confirmación.
 */
export function MembersTab() {
  const members = useMembersQuery();
  const changeStatus = useChangeMemberStatus();
  const currentUserId = useSessionStore((s) => s.user?.id);
  const [query, setQuery] = useState("");
  const [target, setTarget] = useState<MemberResponse | null>(null);

  const visible = filterMembers(members.data ?? [], query);
  const deactivating = target?.status === "ACTIVE";

  const confirm = () => {
    if (!target?.id) return;
    changeStatus.mutate({ id: target.id, active: !deactivating }, { onSettled: () => setTarget(null) });
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="relative">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <Input
          type="search"
          className="h-11 rounded-xl pl-10"
          placeholder="Buscar por nombre o correo…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Buscar en el personal"
        />
      </div>

      <div className="overflow-hidden rounded-2xl border bg-card shadow-xs">
        {members.isLoading ? (
          <StaffListSkeleton />
        ) : members.isError ? (
          <StaffListError what="al personal" />
        ) : visible.length === 0 ? (
          <MembersSearchEmpty onClear={() => setQuery("")} />
        ) : (
          <ul className="divide-y" aria-label="Personal de la clínica">
            <AnimatePresence initial={false}>
              {visible.map((m) => (
                <MemberRow key={m.id} member={m} isSelf={m.id === currentUserId} onToggleStatus={setTarget} />
              ))}
            </AnimatePresence>
          </ul>
        )}
      </div>

      <ConfirmDialog
        open={target !== null}
        onOpenChange={(open) => (open ? null : setTarget(null))}
        title={deactivating ? `¿Desactivar a ${target?.fullName}?` : `¿Reactivar a ${target?.fullName}?`}
        description={
          deactivating
            ? "No podrá iniciar sesión en la clínica. Lo que registró se conserva y puedes reactivarlo cuando quieras."
            : "Podrá volver a iniciar sesión en la clínica con su cuenta."
        }
        confirmLabel={deactivating ? "Desactivar" : "Reactivar"}
        destructive={deactivating}
        pending={changeStatus.isPending}
        onConfirm={confirm}
      />
    </div>
  );
}
