import type { ImgHTMLAttributes } from "react";
import logoLightUrl from "@/assets/brand/logo-light.png";
import logoDarkUrl from "@/assets/brand/logo-dark.png";
import { cn } from "@/modules/core/utils/cn";

interface LogoProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, "src" | "alt"> {
  /**
   * `auto` (por defecto) sigue el modo claro/oscuro de la app (alterna entre las dos imágenes
   * vía `dark:`). `light`/`dark` fuerzan una variante fija, para paneles con un color de fondo
   * propio que no está atado al tema — p. ej. el panel de marca del login, siempre oscuro.
   */
  variant?: "auto" | "light" | "dark";
}

/** Wordmark real de Dentdesk (PNG con transparencia, dos variantes según el fondo). */
export function Logo({ className, variant = "auto", ...props }: LogoProps) {
  if (variant === "light") {
    return <img src={logoLightUrl} alt="Dentdesk" className={cn("h-8 w-auto", className)} {...props} />;
  }
  if (variant === "dark") {
    return <img src={logoDarkUrl} alt="Dentdesk" className={cn("h-8 w-auto", className)} {...props} />;
  }
  return (
    <>
      <img
        src={logoLightUrl}
        alt="Dentdesk"
        className={cn("h-8 w-auto dark:hidden", className)}
        {...props}
      />
      {/* Duplicado solo visual (alterna por CSS): una sola imagen debe tener nombre accesible. */}
      <img
        src={logoDarkUrl}
        alt=""
        aria-hidden="true"
        className={cn("hidden h-8 w-auto dark:block", className)}
        {...props}
      />
    </>
  );
}
