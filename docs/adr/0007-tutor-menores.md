# ADR-0007: Tutor/responsable para pacientes menores de edad

- **Estado:** Aceptado — **núcleo construido**; responsabilidades legales pendientes (ver abajo)
- **Fecha:** 2026-07-23
- **Decisores:** David Velarde Alvarado

## Contexto

Los menores de edad requieren un representante legal que autorice tratamientos y sirva como contacto principal. Hay que decidir **cómo se modela** esa relación sin duplicar la historia clínica.

## Decisión

- El **paciente sigue siendo el menor**. La historia clínica pertenece al niño.
- El **tutor** se registra como **responsable legal asociado** al paciente (no es un paciente aparte, ni un usuario del sistema).
- El tutor puede: autorizar procedimientos, firmar consentimientos, actuar como contacto principal y asumir responsabilidades administrativas cuando corresponda.

## Consecuencias

- ✅ Separa claramente al **paciente** (el menor) del **responsable legal** (el tutor).
- ✅ Evita duplicar historias clínicas: no se crea un "paciente tutor".
- ⚠️ La minoría de edad **se deriva de la fecha de nacimiento**, no se declara — un dato declarado se desincroniza el día del cumpleaños.

## Relación con lo construido

- ✅ **Construido:** `patients` ya **exige responsable cuando el paciente es menor**, con la minoría **derivada** de la fecha de nacimiento (spec `patients`, requirement "Un paciente menor de edad exige un responsable"; schema Zod `superRefine`). Los datos del responsable construidos: **nombre, parentesco, teléfono** (y documento opcional).
- 🔮 **Pendiente:** las **responsabilidades legales** del tutor —**firmar consentimientos**, autorizar procedimientos— dependen de la capacidad de **consentimientos/tratamientos**, aún no construida. Hoy el tutor es un contacto responsable; el flujo de firma no existe todavía.

**Este ADR está mayormente cumplido por `patients`.** La parte de consentimientos NO forma parte del change `update-patients`: llegará con `treatments`/consentimientos.

## Alternativas descartadas

- **Registrar al tutor como un `Patient` más y enlazarlos** — rechazada: duplica fichas y confunde de quién es la historia clínica (es del menor, siempre).
- **Declarar `isMinor` como campo** en vez de derivarlo — rechazada: se desincroniza el día del cumpleaños; la edad se deriva de `birthDate`.
