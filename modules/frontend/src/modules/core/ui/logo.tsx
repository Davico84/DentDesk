import type { ImgHTMLAttributes } from "react";
import { cn } from "@/modules/core/utils/cn";
import logoLight from "@/assets/brand/logo-light.png";
import logoDark from "@/assets/brand/logo-dark.png";
import logoWhite from "@/assets/brand/logo-white.png";

interface LogoProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, "src" | "alt"> {
  /** `auto` (por tema) o `white` (silueta blanca, para fondos de color/gradiente). */
  variant?: "auto" | "white";
}

/** Logo de OdontoRisas. `auto` muestra la variante clara u oscura según el tema. */
export function Logo({ className, variant = "auto", ...props }: LogoProps) {
  if (variant === "white") {
    return <img src={logoWhite} alt="OdontoRisas" className={className} {...props} />;
  }
  return (
    <>
      <img src={logoLight} alt="OdontoRisas" className={cn("dark:hidden", className)} {...props} />
      <img src={logoDark} alt="OdontoRisas" className={cn("hidden dark:block", className)} {...props} />
    </>
  );
}
