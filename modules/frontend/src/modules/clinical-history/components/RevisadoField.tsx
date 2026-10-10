import { useWatch, type Control, useForm } from "react-hook-form";
import { CircleAlert, ShieldCheck } from "lucide-react";
import { cn } from "@/modules/core/utils/cn";
import type { ClinicalHistoryValues } from "@/modules/clinical-history/schemas/clinicalHistory";

type RevisadoName = "alergiasRevisado" | "medicacionRevisado" | "antecedentesFamiliaresRevisado";
type DetalleName = "alergiasDetalle" | "medicacionDetalle" | "antecedentesFamiliaresDetalle";

/**
 * El par revisado/detalle del patrón "campo vacío ≠ revisado" (design.md D3): un dato clínico
 * afirmativo ("sin alergias conocidas") es distinto de un campo que nadie llenó todavía. El
 * copy y el color lo dicen explícito — no es un checkbox genérico de formulario.
 */
export function RevisadoField({
  label,
  revisadoName,
  detalleName,
  control,
  register,
}: {
  label: string;
  revisadoName: RevisadoName;
  detalleName: DetalleName;
  control: Control<ClinicalHistoryValues>;
  register: ReturnType<typeof useForm<ClinicalHistoryValues>>["register"];
}) {
  const revisado = useWatch({ control, name: revisadoName });

  return (
    <div
      className={cn(
        "flex flex-col gap-2 rounded-xl border p-3 transition-colors sm:col-span-2",
        revisado ? "border-success/30 bg-success/5" : "border-border",
      )}
    >
      <label className="flex min-h-11 cursor-pointer items-center gap-2 text-sm font-medium">
        <input type="checkbox" className="size-5 rounded border-input accent-success" {...register(revisadoName)} />
        {revisado ? (
          <ShieldCheck className="size-4 text-success" aria-hidden="true" />
        ) : (
          <CircleAlert className="size-4 text-muted-foreground" aria-hidden="true" />
        )}
        <span>{label}</span>
        <span className="text-xs font-normal text-muted-foreground">
          {revisado ? "— revisado" : "— sin revisar"}
        </span>
      </label>
      <textarea
        rows={2}
        disabled={!revisado}
        placeholder={revisado ? `Sin hallazgos si no hay nada que registrar en «${label.toLowerCase()}»` : "Marca «revisado» para poder registrar este dato"}
        className="w-full resize-y rounded-xl border border-input bg-card px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
        {...register(detalleName)}
      />
    </div>
  );
}
