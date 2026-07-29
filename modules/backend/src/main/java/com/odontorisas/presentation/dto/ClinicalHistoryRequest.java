package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Guardado del documento inicial de la Historia Clínica en {@code DRAFT} (design.md D1/D2/D3).
 * Sin {@code @NotBlank}/{@code @NotNull}: el guardado incremental acepta secciones vacías — el
 * checklist mínimo solo se exige al sellar ({@code POST .../complete}), no aquí.
 *
 * <p>Los pares {@code *Revisado}/{@code *Detalle} son el patrón "campo vacío ≠ revisado"
 * (design.md D3): {@code revisado=true, detalle=null} ⇒ "sin alergias conocidas";
 * {@code revisado=false} ⇒ no revisado.
 */
public record ClinicalHistoryRequest(
    String motivoConsulta,
    String enfermedadActual,
    String antecedentesPersonales,
    String examenClinico,
    String pronostico,
    @Schema(description = "Estructura libre (PA, FC, temperatura...), sin queries por dentro.")
    Map<String, Object> signosVitales,
    @Schema(description = "Ausente o null = no revisado, igual que false. Nunca se exige en el guardado incremental.")
    Boolean alergiasRevisado,
    String alergiasDetalle,
    Boolean medicacionRevisado,
    String medicacionDetalle,
    Boolean antecedentesFamiliaresRevisado,
    String antecedentesFamiliaresDetalle) {
}
