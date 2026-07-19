import type { ComponentType, ReactNode } from "react";
import type { LucideProps } from "lucide-react";
import { cn } from "@/modules/core/utils/cn";

const ICON_VARIANTS = {
  default: "bg-primary/5 text-primary",
  destructive: "bg-destructive/10 text-destructive",
  muted: "bg-muted text-muted-foreground",
} as const;

interface DataStateProps {
  icon: ComponentType<LucideProps>;
  title: string;
  description: string;
  variant?: keyof typeof ICON_VARIANTS;
  action?: ReactNode;
}

/**
 * Shell genérico para los estados async no-loading (error/vacío/no-encontrado): ícono + título +
 * descripción + acción opcional. No conoce el dominio (`core/components`, docs/frontend.md §4.1);
 * cada capacidad compone su contenido encima con su propio archivo (docs/frontend.md §1).
 */
export function DataState({ icon: Icon, title, description, variant = "default", action }: DataStateProps) {
  return (
    <div className="flex flex-col items-center gap-4 px-6 py-16 text-center">
      <span className={cn("flex size-14 items-center justify-center rounded-full", ICON_VARIANTS[variant])}>
        <Icon className="size-7" />
      </span>
      <div className="space-y-1">
        <p className="font-medium">{title}</p>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  );
}
