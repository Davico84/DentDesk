package com.odontorisas.service.clinicalhistory;

import java.util.List;

/**
 * Un código CIE10 encontrado, con su <b>ruta jerárquica completa</b> (capítulo → grupo →
 * categoría → [subcategoría]) — el buscador no puede aplanar la jerarquía a una lista plana
 * (spec: búsqueda jerárquica).
 */
public record Cie10SearchResult(String code, String descripcion, String nivel, List<Cie10PathSegment> path) {

    public record Cie10PathSegment(String code, String descripcion, String nivel) {
    }
}
