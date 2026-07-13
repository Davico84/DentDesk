import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { CSSProperties } from "react";
import { Link } from "react-router-dom";
import { Loader2, ArrowLeft } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { Label } from "@/modules/core/ui/label";
import { Input } from "@/modules/core/ui/input";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/modules/core/ui/card";
import { PATHS } from "@/routes/paths";
import { deriveTheme } from "@/modules/core/theme/deriveTheme";
import { brandingSchema, toRequest, type BrandingValues } from "@/modules/branding/schemas/branding";
import { applyServerFieldErrors } from "@/modules/core/utils/apiError";
import { useBrandingQuery, useUpdateBranding } from "@/modules/branding/hooks/useBrandingApi";

/** Variables CSS (tokens claros) del tema derivado, para el contenedor de vista previa. */
function previewVars(values: BrandingValues): CSSProperties | undefined {
  const theme = deriveTheme({
    brandPrimary: values.brandPrimary || null,
    brandSecondary: values.brandSecondary || null,
  });
  if (!theme) return undefined;
  const vars: Record<string, string> = {};
  for (const [token, value] of Object.entries(theme.light)) vars[`--${token}`] = value;
  return vars as CSSProperties;
}

/** Ajustes de marca de la organización (solo ADMIN): color(es), logo y vista previa en vivo. */
export function BrandingSettingsScreen() {
  const brandingQuery = useBrandingQuery();
  const updateBranding = useUpdateBranding();

  const current = brandingQuery.data;
  const {
    register,
    handleSubmit,
    control,
    setValue,
    setError,
    formState: { errors },
  } = useForm<BrandingValues>({
    resolver: zodResolver(brandingSchema),
    values: {
      brandPrimary: current?.brandPrimary ?? "",
      brandSecondary: current?.brandSecondary ?? "",
      logoUrl: current?.logoUrl ?? "",
    },
  });

  const watched = useWatch({ control }) as BrandingValues;
  const contrastWarning =
    deriveTheme({ brandPrimary: watched.brandPrimary || null, brandSecondary: watched.brandSecondary || null })
      ?.contrastWarning ?? false;

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 p-6">
      <div className="flex items-center gap-3">
        <Link to={PATHS.ROOT} aria-label="Volver" className={buttonVariants({ variant: "ghost", size: "icon" })}>
          <ArrowLeft className="size-4" />
        </Link>
        <h1 className="text-xl font-bold">Marca de la organización</h1>
      </div>

      <form
        onSubmit={handleSubmit((values) =>
          // El 400 del servidor (color con formato inválido, logo que no es https) trae el detalle
          // por campo: se pinta junto al input, igual que el de Zod.
          updateBranding.mutate(toRequest(values), {
            onError: (error) => applyServerFieldErrors(error, setError),
          }),
        )}
        className="flex flex-col gap-4"
        noValidate
      >
        <div className="flex flex-col gap-1.5">
          <Label htmlFor="brandPrimary">Color primario</Label>
          <div className="flex items-center gap-2">
            <input
              type="color"
              aria-label="Selector de color primario"
              className="h-9 w-12 rounded border"
              value={/^#[0-9a-fA-F]{6}$/.test(watched.brandPrimary || "") ? watched.brandPrimary : "#7f1aa6"}
              onChange={(e) => setValue("brandPrimary", e.target.value, { shouldValidate: true })}
            />
            <Input id="brandPrimary" placeholder="#7f1aa6" {...register("brandPrimary")} />
          </div>
          {errors.brandPrimary ? <p className="text-xs text-destructive">{errors.brandPrimary.message}</p> : null}
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="brandSecondary">Color secundario (opcional)</Label>
          <Input id="brandSecondary" placeholder="#16C3B2" {...register("brandSecondary")} />
          {errors.brandSecondary ? <p className="text-xs text-destructive">{errors.brandSecondary.message}</p> : null}
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="logoUrl">URL del logo (https)</Label>
          <Input id="logoUrl" placeholder="https://cdn.tuclinica.com/logo.png" {...register("logoUrl")} />
          {errors.logoUrl ? <p className="text-xs text-destructive">{errors.logoUrl.message}</p> : null}
        </div>

        {contrastWarning ? (
          <p className="rounded-md bg-warning/10 px-3 py-2 text-sm text-warning-foreground">
            ⚠ Este color no ofrece suficiente contraste como color primario; el texto podría costar leerse.
          </p>
        ) : null}

        <Button type="submit" disabled={updateBranding.isPending} className="self-start">
          {updateBranding.isPending ? <Loader2 className="mr-2 size-4 animate-spin" /> : null}
          Guardar marca
        </Button>
      </form>

      {/* Vista previa en vivo: aplica el tema derivado SOLO a este contenedor (sin guardar). */}
      <Card style={previewVars(watched)}>
        <CardHeader>
          <CardTitle className="text-primary">Vista previa</CardTitle>
          <CardDescription>Así se verían los elementos con esta marca.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-center gap-3">
          <Button>Botón primario</Button>
          <span className="rounded-md bg-accent px-3 py-1 text-accent-foreground">Acento</span>
          <span className="rounded-md bg-secondary px-3 py-1 text-secondary-foreground">Secundario</span>
        </CardContent>
      </Card>
    </main>
  );
}
