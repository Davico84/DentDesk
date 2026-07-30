package com.odontorisas.presentation.dto;

import com.odontorisas.service.clinicalhistory.Cie10SearchResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Resultado de búsqueda CIE10 con su ruta jerárquica completa (spec: búsqueda jerárquica). El
 * buscador del frontend NO debe aplanar esto a una lista sin contexto.
 */
public record Cie10SearchResultResponse(
    String code,
    String descripcion,
    @Schema(description = "CAPITULO | GRUPO | CATEGORIA | SUBCATEGORIA")
    String nivel,
    @Schema(description = "Ruta desde el capítulo hasta este código, en orden.")
    List<PathSegmentResponse> path) {

    public record PathSegmentResponse(String code, String descripcion, String nivel) {
        static PathSegmentResponse from(Cie10SearchResult.Cie10PathSegment s) {
            return new PathSegmentResponse(s.code(), s.descripcion(), s.nivel());
        }
    }

    public static Cie10SearchResultResponse from(Cie10SearchResult r) {
        return new Cie10SearchResultResponse(
            r.code(), r.descripcion(), r.nivel(),
            r.path().stream().map(PathSegmentResponse::from).toList());
    }
}
