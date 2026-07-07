import type { ImgHTMLAttributes } from "react";
import { cn } from "@/modules/core/utils/cn";
import logoLight from "@/assets/brand/logo-light.png";
import logoDark from "@/assets/brand/logo-dark.png";

type LogoProps = Omit<ImgHTMLAttributes<HTMLImageElement>, "src" | "alt">;

/** Logo de OdontoRisas; muestra la variante clara u oscura según el tema activo. */
export function Logo({ className, ...props }: LogoProps) {
  return (
    <>
      <img src={logoLight} alt="OdontoRisas" className={cn("dark:hidden", className)} {...props} />
      <img src={logoDark} alt="OdontoRisas" className={cn("hidden dark:block", className)} {...props} />
    </>
  );
}
