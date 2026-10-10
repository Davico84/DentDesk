import type { LucideIcon } from "lucide-react";
import { ArrowRight } from "lucide-react";
import { Link, type LinkProps } from "react-router-dom";
import { cn } from "@/modules/core/utils/cn";

/**
 * Tarjeta de atajo: un enlace grande con ícono, título y una línea que dice qué hay al otro lado.
 * Componente genérico de `core/components`: no conoce ningún dominio.
 */
export function ShortcutCard({
  to,
  state,
  icon: Icon,
  title,
  description,
}: {
  to: LinkProps["to"];
  state?: LinkProps["state"];
  icon: LucideIcon;
  title: string;
  description: string;
}) {
  return (
    <Link
      to={to}
      state={state}
      className={cn(
        "group flex h-full items-start gap-4 rounded-2xl border border-border bg-card p-5 shadow-xs transition-colors",
        "hover:border-primary/40 hover:bg-primary/5",
        "focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring",
      )}
    >
      <span className="inline-flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
        <Icon className="size-5" aria-hidden="true" />
      </span>
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="font-semibold text-foreground">{title}</span>
        <span className="text-sm text-muted-foreground">{description}</span>
      </span>
      <ArrowRight
        className="mt-1 size-4 shrink-0 text-muted-foreground transition-transform group-hover:translate-x-0.5 group-hover:text-primary motion-reduce:transition-none"
        aria-hidden="true"
      />
    </Link>
  );
}
