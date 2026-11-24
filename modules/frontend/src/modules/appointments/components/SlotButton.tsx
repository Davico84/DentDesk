import { cn } from "@/modules/core/utils/cn";
import type { StartOption } from "../utils/agendaLayout";

/** Una hora de inicio posible: elegida, libre, ocupada por el odontólogo o ya pasada. */
export function SlotButton({
  slot,
  selected,
  onSelect,
}: {
  slot: StartOption;
  selected: boolean;
  onSelect: (time: string) => void;
}) {
  const unavailable = slot.busy || slot.past;
  const state = slot.busy ? ", ocupado" : slot.past ? ", ya pasó" : "";
  return (
    <button
      type="button"
      aria-pressed={selected}
      aria-label={`${slot.time}${state}`}
      disabled={unavailable}
      onClick={() => onSelect(slot.time)}
      className={cn(
        "h-9 rounded-lg border text-sm tabular-nums transition-colors focus-visible:outline-hidden focus-visible:ring-2 focus-visible:ring-ring",
        selected
          ? "border-primary bg-primary text-primary-foreground"
          : "border-input bg-card hover:border-primary/60 hover:bg-accent",
        unavailable && "cursor-not-allowed border-dashed bg-muted text-muted-foreground line-through hover:border-input hover:bg-muted",
      )}
    >
      {slot.time}
    </button>
  );
}
