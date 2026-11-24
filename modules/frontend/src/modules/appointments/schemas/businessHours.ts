import { z } from "zod";

/**
 * Paridad con `UpdateBusinessHoursRequest` y la validación de `BusinessHours` del backend: horas `HH:mm` en múltiplos
 * de 5 minutos, hasta 6 tramos por día, apertura anterior al cierre y tramos del mismo día sin solaparse (contiguos
 * sí). Un día sin tramos está cerrado.
 */
const TIME_ON_GRID = /^([01]\d|2[0-3]):[0-5][05]$/;
const GRID_MESSAGE = "Hora HH:mm en múltiplos de 5 minutos";

const segmentSchema = z.object({
  opens: z.string().regex(TIME_ON_GRID, GRID_MESSAGE),
  closes: z.string().regex(TIME_ON_GRID, GRID_MESSAGE),
});

const daySchema = z
  .object({
    day: z.enum(["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"]),
    segments: z.array(segmentSchema).max(6, "Como máximo 6 tramos por día"),
  })
  .superRefine((day, ctx) => {
    day.segments.forEach((segment, index) => {
      if (TIME_ON_GRID.test(segment.opens) && TIME_ON_GRID.test(segment.closes) && segment.opens >= segment.closes) {
        ctx.addIssue({
          code: "custom",
          path: ["segments", index, "closes"],
          message: "El cierre debe ser posterior a la apertura",
        });
      }
    });
    const ordered = day.segments
      .map((segment, index) => ({ ...segment, index }))
      .sort((a, b) => a.opens.localeCompare(b.opens));
    for (let i = 1; i < ordered.length; i++) {
      if (ordered[i].opens < ordered[i - 1].closes) {
        ctx.addIssue({
          code: "custom",
          path: ["segments", ordered[i].index, "opens"],
          message: "Se solapa con otro tramo del mismo día",
        });
      }
    }
  });

/** Los siete días, una vez cada uno (el backend rechaza un día repetido). */
export const businessHoursSchema = z.object({
  days: z
    .array(daySchema)
    .length(7)
    .refine((days) => new Set(days.map((d) => d.day)).size === 7, "Cada día de la semana debe aparecer una sola vez"),
});

export type BusinessHoursValues = z.infer<typeof businessHoursSchema>;
