import { useState } from "react";
import { Logo } from "@/modules/core/ui/logo";
import { useBrandingStore } from "@/store/useBrandingStore";

interface OrgLogoProps {
  className?: string;
}

/**
 * Logo de la organización activa. Muestra su {@code logoUrl} si existe; si la imagen no
 * carga (URL borrada, host caído) cae al logo del producto (design.md D7 — nunca imagen rota).
 * Sin logo definido → logo del producto. Rastrea la URL que falló (no un booleano) para
 * reintentar automáticamente cuando cambia el logo, sin efectos.
 */
export function OrgLogo({ className }: OrgLogoProps) {
  const logoUrl = useBrandingStore.use.logoUrl();
  const [failedUrl, setFailedUrl] = useState<string | null>(null);

  if (logoUrl && logoUrl !== failedUrl) {
    return (
      <img
        src={logoUrl}
        alt="Logo de la organización"
        className={className}
        onError={() => setFailedUrl(logoUrl)}
      />
    );
  }
  return <Logo className={className} />;
}
