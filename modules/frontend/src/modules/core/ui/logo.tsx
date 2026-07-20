import type { SVGProps } from "react";
import { cn } from "@/modules/core/utils/cn";

interface LogoProps extends Omit<SVGProps<SVGSVGElement>, "viewBox"> {
  /** `auto` (token `--primary`, sigue el tema) o `white` (silueta blanca fija, para fondos de color/gradiente). */
  variant?: "auto" | "white";
}

/**
 * Wordmark interino de Dentdesk (ícono + texto en un solo SVG, `currentColor`): reemplaza los PNG
 * de marca hasta que exista el asset real. Escala como imagen (`h-12 w-auto` en el `className`
 * de quien lo usa) porque es un único elemento con `viewBox`, no un ícono + texto por separado.
 */
export function Logo({ className, variant = "auto", ...props }: LogoProps) {
  return (
    <svg
      viewBox="0 0 148 32"
      className={cn("h-8 w-auto", variant === "white" ? "text-white" : "text-primary", className)}
      role="img"
      aria-label="Dentdesk"
      fill="none"
      {...props}
    >
      {/* Ícono: silueta de diente */}
      <path
        d="M16 4c-3.1 0-4.7 1.4-6.8 1.4-1.7 0-3.2-.9-4.3.3-1.2 1.3-.8 4.2-.3 7.3.4 2.5.3 3.8.9 6.7.5 2.5.8 6.1 2.5 8.3.6.9 2.1 1 2.7-.1.9-1.7.9-4.2 1.4-6.1.4-1.3.8-2.2 1.4-2.2s1 .9 1.4 2.2c.5 1.9.5 4.4 1.4 6.1.6 1.1 2.1 1 2.7-.1 1.7-2.2 2-5.8 2.5-8.3.6-2.9.5-4.2.9-6.7.5-3.1.9-6-.3-7.3-1.1-1.2-2.6-.3-4.3-.3C20.7 5.4 19.1 4 16 4Z"
        fill="currentColor"
      />
      {/* Texto: "Dentdesk" */}
      <text
        x="34"
        y="22"
        fontFamily="ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif"
        fontSize="17"
        fontWeight="700"
        letterSpacing="-0.02em"
        fill="currentColor"
      >
        Dentdesk
      </text>
    </svg>
  );
}
