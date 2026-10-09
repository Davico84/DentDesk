import type { ReactNode } from "react";
import { Check } from "lucide-react";
import { INK, INK_2, PAPER, SELECTION } from "@/modules/dental-chart/utils/chartPalette";

/** Un paso numerado del aviso del modo rango: pendiente, en curso (resaltado) o hecho (con check). */
export function RangeModeStep({
  number,
  done,
  active,
  children,
}: {
  number: number;
  done: boolean;
  active: boolean;
  children: ReactNode;
}) {
  const filled = done || active;
  return (
    <li
      className="flex items-center gap-2"
      aria-current={active ? "step" : undefined}
      style={{ color: filled ? INK : INK_2, fontWeight: active ? 600 : 400 }}
    >
      <span
        aria-hidden="true"
        className="inline-flex size-5 shrink-0 items-center justify-center rounded-full border text-xs font-bold"
        style={{ background: filled ? SELECTION : PAPER, borderColor: SELECTION, color: filled ? PAPER : SELECTION }}
      >
        {done ? <Check className="size-3" strokeWidth={3} /> : number}
      </span>
      <span>{children}</span>
    </li>
  );
}
