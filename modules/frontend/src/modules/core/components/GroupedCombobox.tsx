import { useEffect, useRef, useState, type FocusEvent, type KeyboardEvent } from "react";
import { ChevronDown } from "lucide-react";
import { Label } from "@/modules/core/ui/label";
import { cn } from "@/modules/core/utils/cn";
import { normalizeForSearch } from "@/modules/core/utils/search";

export type ComboboxOption = { value: string; label: string; hint?: string };
export type ComboboxGroup = { id: string; label: string; options: ComboboxOption[] };

/**
 * Selector con búsqueda (patrón combobox de ARIA 1.2) con opciones agrupadas. La lista va **en línea**, debajo del
 * campo, sin portal: dentro de un `Dialog` de Radix no compite por el foco y a 375 px queda en el flujo del diálogo.
 *
 * - Escribir filtra sin distinguir tildes; ↑/↓ mueven la opción activa, Enter elige, Escape cierra la lista.
 * - **Escape dentro de un `Dialog`**: Radix lo escucha en `document` en fase de captura, antes que este campo. Quien
 *   lo monta en un diálogo usa `onOpenChange` para saber si la lista está abierta y llama `preventDefault()` en el
 *   `onEscapeKeyDown` del `DialogContent`; si no, Escape cerraría el diálogo entero.
 * - `onBlur` se llama solo cuando el foco sale del combobox completo, no al hacer click en la lista: así la
 *   validación "al tocar" no muestra un error mientras el usuario está eligiendo.
 * - El campo muestra la etiqueta del valor vigente; al salir sin elegir, el texto escrito se descarta.
 */
export function GroupedCombobox({
  id,
  label,
  groups,
  value,
  onSelect,
  onBlur,
  onOpenChange,
  required,
  error,
  disabled,
  placeholder,
  emptyMessage = "No hay resultados.",
}: {
  id: string;
  label: string;
  groups: ComboboxGroup[];
  value: string;
  onSelect: (value: string) => void;
  onBlur?: () => void;
  onOpenChange?: (open: boolean) => void;
  required?: boolean;
  error?: string;
  disabled?: boolean;
  placeholder?: string;
  emptyMessage?: string;
}) {
  const [open, setOpenState] = useState(false);
  const [query, setQuery] = useState<string | null>(null);
  const [active, setActive] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const listId = `${id}-list`;
  const errorId = `${id}-error`;

  const setOpen = (next: boolean) => {
    setOpenState(next);
    onOpenChange?.(next);
  };

  const selectedLabel = groups.flatMap((g) => g.options).find((o) => o.value === value)?.label ?? "";
  const needle = query ? normalizeForSearch(query) : "";
  const visible = groups
    .map((g) => ({ ...g, options: needle ? g.options.filter((o) => normalizeForSearch(o.label).includes(needle)) : g.options }))
    .filter((g) => g.options.length > 0);
  const flat = visible.flatMap((g) => g.options);
  const activeIndex = Math.min(active, flat.length - 1);
  const activeOption = open && activeIndex >= 0 ? flat[activeIndex] : undefined;
  const optionId = (v: string) => `${id}-option-${v}`;
  const activeId = activeOption ? optionId(activeOption.value) : undefined;

  // La opción activa queda a la vista al moverse con las flechas por una lista larga.
  useEffect(() => {
    if (activeId) document.getElementById(activeId)?.scrollIntoView?.({ block: "nearest" });
  }, [activeId]);

  const pick = (option: ComboboxOption) => {
    setQuery(null);
    setOpen(false);
    onSelect(option.value);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      if (!open) {
        setOpen(true);
        setActive(0);
      } else {
        setActive(Math.min(activeIndex + 1, flat.length - 1));
      }
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      if (open) setActive(Math.max(activeIndex - 1, 0));
    } else if (e.key === "Enter") {
      // Enter nunca envía el formulario que lo contiene mientras se elige.
      if (open) {
        e.preventDefault();
        if (activeOption) pick(activeOption);
      }
    } else if (e.key === "Escape") {
      if (open) {
        setOpen(false);
        setQuery(null);
      }
    } else if (e.key === "Tab") {
      if (open) setOpen(false);
    }
  };

  const onContainerBlur = (e: FocusEvent<HTMLDivElement>) => {
    if (containerRef.current?.contains(e.relatedTarget as Node | null)) return;
    if (open) setOpen(false);
    setQuery(null);
    onBlur?.();
  };

  return (
    <div ref={containerRef} className="flex flex-col gap-1.5 sm:col-span-2" onBlur={onContainerBlur}>
      <Label htmlFor={id}>
        {label}
        {required ? <span className="text-destructive"> *</span> : null}
      </Label>
      <div className="relative">
        <input
          id={id}
          type="text"
          role="combobox"
          autoComplete="off"
          aria-autocomplete="list"
          aria-expanded={open}
          aria-controls={listId}
          aria-activedescendant={activeId}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          disabled={disabled}
          placeholder={placeholder}
          className="h-11 w-full rounded-xl border border-input bg-card px-3 pr-9 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
          value={query ?? selectedLabel}
          onChange={(e) => {
            setQuery(e.target.value);
            setActive(0);
            if (!open) setOpen(true);
          }}
          onClick={() => {
            if (open) setQuery(null);
            setOpen(!open);
          }}
          onKeyDown={onKeyDown}
        />
        <ChevronDown aria-hidden="true" className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground" />
      </div>

      {open ? (
        flat.length === 0 ? (
          <p role="status" className="rounded-xl border bg-card px-3 py-2 text-sm text-muted-foreground">
            {emptyMessage}
          </p>
        ) : (
          <div id={listId} role="listbox" aria-label={label} className="max-h-60 overflow-y-auto rounded-xl border bg-card p-1 shadow-sm">
            {visible.map((group) => (
              <div key={group.id} role="group" aria-labelledby={`${id}-group-${group.id}`}>
                <div id={`${id}-group-${group.id}`} role="presentation" className="px-2 pt-2 pb-1 text-xs font-semibold text-muted-foreground">
                  {group.label}
                </div>
                {group.options.map((option) => (
                  <div
                    key={option.value}
                    id={optionId(option.value)}
                    role="option"
                    aria-selected={option.value === value}
                    // El campo no pierde el foco al elegir con el mouse (ver onBlur arriba).
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => pick(option)}
                    className={cn(
                      "flex cursor-pointer flex-col rounded-md px-2 py-1.5 text-sm",
                      option === activeOption ? "bg-accent text-accent-foreground" : "hover:bg-muted",
                    )}
                  >
                    <span className={option.value === value ? "font-medium" : undefined}>{option.label}</span>
                    {option.hint ? <span className="text-xs text-muted-foreground">{option.hint}</span> : null}
                  </div>
                ))}
              </div>
            ))}
          </div>
        )
      ) : null}

      {error ? (
        <p id={errorId} role="alert" className="text-xs text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  );
}
