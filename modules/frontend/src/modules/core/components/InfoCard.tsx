import type { ReactNode } from "react";

/**
 * Tarjeta de "etiqueta → valor" para vistas de detalle (consulta de una ficha). Componente
 * genérico de `core/components`: no conoce ningún dominio.
 */
export function InfoCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="rounded-2xl border bg-card p-5 shadow-xs">
      <h2 className="mb-3 text-sm font-semibold">{title}</h2>
      <dl className="flex flex-col gap-2.5 text-sm">{children}</dl>
    </section>
  );
}

/** Fila etiqueta → valor; muestra "—" cuando el dato está vacío. */
export function InfoRow({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-right font-medium">{value && value.length > 0 ? value : "—"}</dd>
    </div>
  );
}
