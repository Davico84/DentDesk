import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { CSSProperties } from "react";
import { Link } from "react-router-dom";
import { Loader2, ArrowLeft, Check } from "lucide-react";
import { Button } from "@/modules/core/ui/button";
import { buttonVariants } from "@/modules/core/ui/button-variants";
import { Label } from "@/modules/core/ui/label";
import { Input } from "@/modules/core/ui/input";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/modules/core/ui/card";
import { PATHS } from "@/routes/paths";
import { cn } from "@/modules/core/utils/cn";
import { deriveTheme } from "@/modules/core/theme/deriveTheme";
import { brandingSchema, toRequest, type BrandingValues } from "@/modules/branding/schemas/branding";
import { BRAND_PRESETS, findPreset } from "@/modules/branding/presets";
import { applyServerFieldErrors } from "@/modules/core/utils/apiError";
import { useBrandingQuery, useUpdateBranding } from "@/modules/branding/hooks/useBrandingApi";
import { PageContainer } from "@/modules/core/components/PageContainer";

/** Variables CSS (tokens claros) del tema derivado, para el contenedor de vista previa. */
function previewVars(values: BrandingValues): CSSProperties | undefined {
  const preset = findPreset(values.brandPreset);
  const theme = deriveTheme({ brandPrimary: preset?.brandPrimary, brandSecondary: preset?.brandSecondary });
  if (!theme) return undefined;
  const vars: Record<string, string> = {};
  for (const [token, value] of Object.entries(theme.light)) vars[`--${token}`] = value;
  return vars as CSSProperties;
}

/** Ajustes de marca de la organización (solo ADMIN): preset de color, logo y vista previa en vivo. */
export function BrandingSettingsScreen() {
  const brandingQuery = useBrandingQuery();
  const updateBranding = useUpdateBranding();

  const current = brandingQuery.data;
  const {
    handleSubmit,
    control,
    setValue,
    setError,
    register,
    formState: { errors },
  } = useForm<BrandingValues>({
    resolver: zodResolver(brandingSchema),
    values: {
      brandPreset: current?.brandPreset ?? "",
      logoUrl: current?.logoUrl ?? "",
    },
  });

  const watched = useWatch({ control }) as BrandingValues;
  const contrastWarning =
    deriveTheme({
      brandPrimary: findPreset(watched.brandPreset)?.brandPrimary,
      brandSecondary: findPreset(watched.brandPreset)?.brandSecondary,
    })?.contrastWarning ?? false;

  return (
    <PageContainer>
      <div className="flex items-center gap-3">
        <Link to={PATHS.ROOT} aria-label="Volver" className={buttonVariants({ variant: "ghost", size: "icon" })}>
          <ArrowLeft className="size-4" />
        </Link>
        <h1 className="text-xl font-bold">Marca de la organización</h1>
      </div>

      <form
        onSubmit={handleSubmit((values) =>
          // El 400 del servidor (logo que no es https) trae el detalle por campo: se pinta
          // junto al input, igual que el de Zod.
          updateBranding.mutate(toRequest(values), {
            onError: (error) => applyServerFieldErrors(error, setError),
          }),
        )}
        className="flex flex-col gap-4"
        noValidate
      >
        <div className="flex flex-col gap-2">
          <Label>Color de marca</Label>
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Color de marca">
            <button
              type="button"
              role="radio"
              aria-checked={watched.brandPreset === ""}
              onClick={() => setValue("brandPreset", "", { shouldValidate: true })}
              className={cn(
                "flex h-11 items-center gap-2 rounded-xl border px-3 text-sm font-medium transition-colors",
                watched.brandPreset === ""
                  ? "border-primary/40 bg-primary/5 text-primary"
                  : "border-input bg-card text-muted-foreground hover:bg-muted/50",
              )}
            >
              {watched.brandPreset === "" ? <Check className="size-4" /> : null}
              Ninguno (default)
            </button>
            {BRAND_PRESETS.map((preset) => {
              const selected = watched.brandPreset === preset.key;
              return (
                <button
                  key={preset.key}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  onClick={() => setValue("brandPreset", preset.key, { shouldValidate: true })}
                  className={cn(
                    "flex h-11 items-center gap-2 rounded-xl border px-3 text-sm font-medium transition-colors",
                    selected ? "border-primary/40 bg-primary/5" : "border-input bg-card hover:bg-muted/50",
                  )}
                >
                  <span
                    className="size-5 shrink-0 rounded-full border border-black/10"
                    style={{ backgroundColor: preset.brandPrimary }}
                    aria-hidden="true"
                  />
                  {preset.label}
                  {selected ? <Check className="size-4 text-primary" /> : null}
                </button>
              );
            })}
          </div>
          {errors.brandPreset ? <p className="text-xs text-destructive">{errors.brandPreset.message}</p> : null}
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
    </PageContainer>
  );
}
