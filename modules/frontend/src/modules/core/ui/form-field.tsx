import { useState, type ReactNode } from "react";
import type { FieldError, FieldValues, Path, UseFormRegister } from "react-hook-form";
import { ChevronDown } from "lucide-react";
import { cn } from "@/modules/core/utils/cn";
import { Label } from "./label";
import { Input } from "./input";

/** Opción de un {@link SelectField}: el valor que viaja y la etiqueta que se lee. */
export type SelectOption = { value: string; label: string };

type BaseProps<T extends FieldValues> = {
  label: string;
  name: Path<T>;
  register: UseFormRegister<T>;
  error?: FieldError;
  required?: boolean;
  /** Texto de ayuda bajo el campo (formato esperado, etc.). */
  hint?: string;
  /** Ocupa las dos columnas de la rejilla de `FormSection`. */
  wide?: boolean;
};

/**
 * Campo de formulario genérico (label + input + error), reutilizable por cualquier capacidad.
 * Primitivo de `core/ui`: no conoce ningún dominio.
 *
 * <p><b>La validación no vive aquí</b> — vive en el schema Zod de cada formulario; este componente
 * solo <b>pinta</b> el error que le llega (de Zod o del servidor vía `applyServerFieldErrors`).
 *
 * <p><b>Accesibilidad:</b> el error se anuncia (`role="alert"`), el input se marca `aria-invalid` y
 * queda enlazado a su error y a su ayuda con `aria-describedby`. Un borde rojo sin anuncio deja
 * fuera a quien usa lector de pantalla.
 */
export function FormField<T extends FieldValues>({
  label,
  name,
  register,
  error,
  type,
  required,
  placeholder,
  hint,
  wide,
  maxLength,
  inputMode,
}: BaseProps<T> & {
  type?: string;
  placeholder?: string;
  /** Tope duro de caracteres: impedir que se pase es mejor que avisarle después. */
  maxLength?: number;
  inputMode?: "text" | "numeric" | "tel" | "email";
}) {
  const errorId = `${name}-error`;
  const hintId = `${name}-hint`;
  return (
    <FieldShell label={label} name={name} required={required} error={error} hint={hint} wide={wide}>
      <Input
        id={name}
        type={type}
        placeholder={placeholder}
        maxLength={maxLength}
        inputMode={inputMode}
        className="h-11 rounded-xl"
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy(error ? errorId : undefined, hint ? hintId : undefined)}
        {...register(name)}
      />
    </FieldShell>
  );
}

/**
 * Campo de selección sobre un <b>conjunto cerrado</b> (un enum del contrato). Usa un `<select>`
 * nativo: es accesible por teclado y con lector de pantalla sin trabajo extra, y en móvil abre el
 * selector del sistema. Misma accesibilidad de error que {@link FormField}.
 */
export function SelectField<T extends FieldValues>({
  label,
  name,
  register,
  error,
  options,
  placeholder = "Sin especificar",
  required,
  hint,
  wide,
}: BaseProps<T> & { options: SelectOption[]; placeholder?: string }) {
  const errorId = `${name}-error`;
  const hintId = `${name}-hint`;
  return (
    <FieldShell label={label} name={name} required={required} error={error} hint={hint} wide={wide}>
      <div className="relative">
        <select
          id={name}
          className="h-11 w-full appearance-none rounded-xl border border-input bg-card px-3 pr-9 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy(error ? errorId : undefined, hint ? hintId : undefined)}
          {...register(name)}
        >
          <option value="">{placeholder}</option>
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
      </div>
    </FieldShell>
  );
}

/** Marco común: etiqueta, control, ayuda y error anunciado. */
function FieldShell({
  label,
  name,
  required,
  error,
  hint,
  wide,
  children,
}: {
  label: string;
  name: string;
  required?: boolean;
  error?: FieldError;
  hint?: string;
  wide?: boolean;
  children: ReactNode;
}) {
  return (
    <div className={["flex flex-col gap-1.5", wide ? "sm:col-span-2" : ""].join(" ")}>
      <Label htmlFor={name}>
        {label}
        {required ? <span className="text-destructive"> *</span> : null}
      </Label>
      {children}
      {hint && !error ? (
        <p id={`${name}-hint`} className="text-xs text-muted-foreground">
          {hint}
        </p>
      ) : null}
      {/* role="alert": el error se ANUNCIA, no solo se pinta (un borde rojo no llega a un lector). */}
      {error ? (
        <p id={`${name}-error`} role="alert" className="text-xs text-destructive">
          {error.message}
        </p>
      ) : null}
    </div>
  );
}

function describedBy(...ids: Array<string | undefined>): string | undefined {
  const present = ids.filter(Boolean);
  return present.length > 0 ? present.join(" ") : undefined;
}

/**
 * Resumen de errores al principio del formulario, con **enlace a cada campo** (WCAG). Solo aparece
 * cuando hay **más de uno**: para un único error, el mensaje junto al campo ya basta y un resumen
 * sería ruido.
 *
 * <p>Los enlaces apuntan al `id` del campo (que es su `name`), así que llevan el foco directo al
 * que falla — útil en un formulario largo donde el error puede quedar fuera de pantalla.
 */
export function FormErrorSummary({
  errors,
  labels,
  title = "Revisa estos campos antes de guardar",
}: {
  errors: Record<string, { message?: string } | undefined>;
  /** Etiqueta legible por campo: el mensaje solo ("Obligatorio") no diría cuál es. */
  labels: Record<string, string>;
  title?: string;
}) {
  const items = Object.entries(errors)
    .filter(([, error]) => error?.message)
    .map(([name, error]) => ({ name, message: error!.message as string }));

  if (items.length < 2) return null;

  return (
    <div role="alert" className="rounded-2xl border border-destructive/30 bg-destructive/5 p-4">
      <p className="text-sm font-semibold text-destructive">{title}</p>
      <ul className="mt-2 space-y-1 text-sm">
        {items.map((item) => (
          <li key={item.name}>
            <a href={`#${item.name}`} className="text-destructive underline underline-offset-2 hover:no-underline">
              {labels[item.name] ?? item.name}
            </a>
            <span className="text-muted-foreground"> — {item.message}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/**
 * Sección de formulario como tarjeta, con rejilla de dos columnas. Primitivo de `core/ui`.
 *
 * <p>Con `collapsible` se pliega para aplicar <b>divulgación progresiva</b>: lo opcional no abruma
 * de entrada, pero sigue a un clic. Se abre **sola si alguno de sus campos tiene error** (`hasError`)
 * — un error escondido en una sección plegada sería una trampa, y además el foco automático al
 * primer campo inválido no podría alcanzarlo.
 */
export function FormSection({
  title,
  description,
  icon,
  collapsible = false,
  defaultOpen = true,
  hasError = false,
  children,
}: {
  title: string;
  description?: string;
  /** Icono de la sección (SVG de lucide — nunca un emoji). Ayuda a escanear el formulario. */
  icon?: ReactNode;
  collapsible?: boolean;
  defaultOpen?: boolean;
  hasError?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  // Un error FUERZA abrir, derivándolo en render (sin efecto ni setState en cascada). Mientras
  // haya error no se puede plegar: esconderlo dejaría al usuario sin saber qué corregir.
  const isOpen = open || hasError;

  const grid = <div className="grid gap-4 sm:grid-cols-2">{children}</div>;

  if (!collapsible) {
    return (
      <section className="rounded-2xl border bg-card p-5 shadow-sm">
        <SectionHeading title={title} description={description} icon={icon} className="mb-4" />
        {grid}
      </section>
    );
  }

  return (
    <section className="overflow-hidden rounded-2xl border bg-card shadow-sm">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={isOpen}
        className="flex w-full cursor-pointer items-center justify-between gap-4 p-5 text-left transition-colors duration-200 hover:bg-muted/30"
      >
        <SectionHeading title={title} description={description} icon={icon} />
        {/* motion-reduce: quien pide menos movimiento no ve girar el chevron (checklist del skill). */}
        <ChevronDown
          className={cn(
            "size-4 shrink-0 text-muted-foreground transition-transform duration-200 motion-reduce:transition-none",
            isOpen && "rotate-180",
          )}
        />
      </button>
      {isOpen ? <div className="px-5 pb-5">{grid}</div> : null}
    </section>
  );
}

function SectionHeading({
  title,
  description,
  icon,
  className,
}: {
  title: string;
  description?: string;
  icon?: ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("flex items-start gap-3", className)}>
      {icon ? (
        <span aria-hidden="true" className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
          {icon}
        </span>
      ) : null}
      <div>
        <h2 className="text-sm font-semibold">{title}</h2>
        {description ? <p className="mt-0.5 text-xs text-muted-foreground">{description}</p> : null}
      </div>
    </div>
  );
}
