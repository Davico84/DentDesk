import type { ReactNode } from "react";
import type { FieldError, FieldValues, Path, UseFormRegister } from "react-hook-form";
import { Label } from "./label";
import { Input } from "./input";

/**
 * Campo de formulario genérico (label + input + error), reutilizable por cualquier capacidad.
 * Es un primitivo de `core/ui`: no conoce ningún dominio. La **validación no vive aquí** — vive en
 * el schema Zod de cada formulario; este componente solo **pinta** el error que le llega (de Zod o
 * del servidor vía `applyServerFieldErrors`), justo debajo del input.
 *
 * @typeParam T - el tipo de valores del formulario (se infiere de `register`).
 */
export function FormField<T extends FieldValues>({
  label,
  name,
  register,
  error,
  type,
  required,
  placeholder,
  wide,
}: {
  label: string;
  name: Path<T>;
  register: UseFormRegister<T>;
  error?: FieldError;
  type?: string;
  required?: boolean;
  placeholder?: string;
  /** Ocupa las dos columnas de la rejilla de `FormSection`. */
  wide?: boolean;
}) {
  return (
    <div className={["flex flex-col gap-1.5", wide ? "sm:col-span-2" : ""].join(" ")}>
      <Label htmlFor={name}>
        {label}
        {required ? <span className="text-destructive"> *</span> : null}
      </Label>
      <Input id={name} type={type} placeholder={placeholder} className="h-11 rounded-xl" {...register(name)} />
      {error ? <p className="text-xs text-destructive">{error.message}</p> : null}
    </div>
  );
}

/** Sección de formulario como tarjeta, con rejilla de dos columnas. Primitivo de `core/ui`. */
export function FormSection({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <section className="rounded-2xl border bg-card p-5 shadow-sm">
      <div className="mb-4">
        <h2 className="text-sm font-semibold">{title}</h2>
        {description ? <p className="mt-0.5 text-xs text-muted-foreground">{description}</p> : null}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">{children}</div>
    </section>
  );
}
