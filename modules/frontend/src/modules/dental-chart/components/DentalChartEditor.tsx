import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeftRight, ClipboardEdit, Loader2, NotebookPen } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { FormSection, TextareaField } from "@/modules/core/ui/form-field";
import {
  dentalChartTextSchema,
  missingDentalChartFields,
  textDefaults,
  toTextRequest,
  type DentalChartTextValues,
} from "@/modules/dental-chart/schemas/dentalChart";
import {
  useDentalChart,
  useDentalFindingCatalog,
  useSetObservations,
  useSetSpecifications,
} from "@/modules/dental-chart/hooks/useDentalChartApi";
import type { DentalChartFindingResponse } from "@/modules/core/services/generated/model";
import { Odontogram } from "./Odontogram";
import { FindingList } from "./FindingList";
import { FindingDialog, type ClickedLocation } from "./FindingDialog";
import { DentalChartStatusBadge } from "./DentalChartStatusBadge";

/** Edición del odontograma ya creado (OPEN editable, SEALED de solo lectura — el sellado se hace junto con la Historia). */
export function DentalChartEditor({ patientId }: { patientId: number }) {
  const { data: block } = useDentalChart(patientId);
  const { data: catalog } = useDentalFindingCatalog();
  const setSpecifications = useSetSpecifications(patientId);
  const setObservations = useSetObservations(patientId);
  // Clickear una pieza/superficie/arco/entre-piezas abre el modal ahí mismo (sin perder de vista
  // el gráfico) en vez de precargar un formulario lejano. Rango es el único que exige dos clicks
  // (dos piezas), por eso necesita un modo explícito — mientras está activo, el odontograma
  // resalta la primera pieza elegida y espera la segunda en la misma fila (Escape cancela).
  const [dialogLocation, setDialogLocation] = useState<ClickedLocation | null>(null);
  const [rangeMode, setRangeMode] = useState(false);

  const sealed = block?.status === "SEALED";
  const missing = missingDentalChartFields(block);
  const findings = block?.findings ?? [];

  const specForm = useForm<DentalChartTextValues>({
    resolver: zodResolver(dentalChartTextSchema),
    values: textDefaults(block?.specifications),
  });
  const obsForm = useForm<DentalChartTextValues>({
    resolver: zodResolver(dentalChartTextSchema),
    values: textDefaults(block?.observations),
  });

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold">Odontograma</h2>
          <p className="text-sm text-muted-foreground">Examinado el {block?.examinedOn}</p>
        </div>
        <DentalChartStatusBadge status={block?.status} />
      </div>

      {!sealed ? (
        <div className="flex flex-wrap items-center gap-2">
          <Button
            type="button"
            size="sm"
            variant={rangeMode ? "default" : "outline"}
            aria-pressed={rangeMode}
            title="Para hallazgos sobre varias piezas contiguas: prótesis parcial, aparato ortodóntico fijo."
            onClick={() => setRangeMode((v) => !v)}
          >
            <ArrowLeftRight className="mr-2 size-4" />
            {rangeMode ? "Cancelar rango" : "Registrar rango"}
          </Button>
          <p className="text-xs text-muted-foreground">
            {rangeMode
              ? "Elegí la primera pieza del tramo y después la última, en la misma fila."
              : "Para prótesis parciales y aparatos que abarcan varias piezas contiguas (ej. 14 a 12)."}
          </p>
        </div>
      ) : null}

      <Odontogram
        findings={findings}
        catalog={catalog}
        interactive={!sealed}
        rangeMode={rangeMode}
        onSelectLocation={(location) => {
          const l = location as ClickedLocation;
          if (l.locationKind === "TOOTH_RANGE") setRangeMode(false);
          setDialogLocation(l);
        }}
      />

      <fieldset disabled={sealed} className="flex flex-col gap-6">
        <FormSection title="Especificaciones" description="Lo que el catálogo no puede dibujar (5.14): material de una corona, clasificación de una movilidad, fluorosis." icon={<ClipboardEdit className="size-4" />}>
          <form
            onSubmit={specForm.handleSubmit((v) => setSpecifications.mutate(toTextRequest(v)))}
            className="sm:col-span-2 flex flex-col gap-2"
            noValidate
          >
            <TextareaField
              label="Especificaciones"
              name="text"
              register={specForm.register}
              error={specForm.formState.errors.text}
              rows={3}
              wide
              hint={block?.specificationsReviewed ? "Revisado" : "Sin revisar — guarda aunque quede vacío para marcarlo como revisado."}
            />
            <div>
              <Button type="submit" size="sm" variant="outline" disabled={setSpecifications.isPending}>
                {setSpecifications.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
                Guardar especificaciones
              </Button>
            </div>
          </form>
        </FormSection>

        <FormSection title="Observaciones" description="Lo que la nomenclatura del odontograma no contempla (5.15)." icon={<NotebookPen className="size-4" />}>
          <form
            onSubmit={obsForm.handleSubmit((v) => setObservations.mutate(toTextRequest(v)))}
            className="sm:col-span-2 flex flex-col gap-2"
            noValidate
          >
            <TextareaField
              label="Observaciones"
              name="text"
              register={obsForm.register}
              error={obsForm.formState.errors.text}
              rows={3}
              wide
              hint={block?.observationsReviewed ? "Revisado" : "Sin revisar — guarda aunque quede vacío para marcarlo como revisado."}
            />
            <div>
              <Button type="submit" size="sm" variant="outline" disabled={setObservations.isPending}>
                {setObservations.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
                Guardar observaciones
              </Button>
            </div>
          </form>
        </FormSection>

        <section className="rounded-2xl border bg-card p-5 shadow-sm">
          <h3 className="mb-1 text-sm font-semibold">Hallazgos</h3>
          <p className="mb-4 text-xs text-muted-foreground">
            Pieza, superficie, arco y entre-piezas se registran clickeando el odontograma. Rango necesita dos clicks — activá "Registrar rango"
            arriba, elegí la primera pieza y después la última.
          </p>
          <FindingList patientId={patientId} findings={findings} catalog={catalog} editable={!sealed} />
        </section>
      </fieldset>

      {!sealed && missing.length > 0 ? (
        <p className="text-xs text-muted-foreground">
          Falta revisar {missing.length === 2 ? "especificaciones y observaciones" : "una sección"} antes de poder sellar el
          odontograma junto con la Historia Clínica.
        </p>
      ) : null}

      {dialogLocation ? (
        <FindingDialog
          key={locationKey(dialogLocation)}
          open
          onOpenChange={(open) => {
            if (!open) setDialogLocation(null);
          }}
          patientId={patientId}
          catalog={catalog}
          location={dialogLocation}
          existingFindings={findingsAt(findings, dialogLocation)}
        />
      ) : null}
    </div>
  );
}

function locationKey(location: ClickedLocation): string {
  const between = [location.toothA, location.toothB].sort((a, b) => (a ?? 0) - (b ?? 0)).join(",");
  const range = [...(location.teeth ?? [])].sort((a, b) => a - b).join(",");
  return `${location.locationKind}:${location.toothA ?? ""}:${location.surface ?? ""}:${location.arch ?? ""}:${between}:${range}`;
}

function findingsAt(findings: DentalChartFindingResponse[], location: ClickedLocation): DentalChartFindingResponse[] {
  return findings.filter((f) => {
    const l = f.location;
    if (!l || l.locationKind !== location.locationKind) return false;
    if (location.locationKind === "TOOTH") return l.toothA === location.toothA;
    if (location.locationKind === "SURFACE") return l.toothA === location.toothA && l.surface === location.surface;
    if (location.locationKind === "ARCH") return l.arch === location.arch;
    if (location.locationKind === "BETWEEN_TEETH") {
      const wanted = new Set([location.toothA, location.toothB]);
      return wanted.has(l.toothA) && wanted.has(l.toothB) && l.toothA !== l.toothB;
    }
    if (location.locationKind === "TOOTH_RANGE") {
      const wanted = new Set(location.teeth ?? []);
      const actual = l.teeth ?? [];
      return actual.length === wanted.size && actual.every((t) => wanted.has(t));
    }
    return false;
  });
}
