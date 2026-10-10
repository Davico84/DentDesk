package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.Cie10SearchResultResponse;
import com.odontorisas.service.clinicalhistory.Cie10SearchService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Búsqueda del catálogo CIE10-MINSA (spec: búsqueda jerárquica, `clinical-history`). Sin
 * {@code @PreAuthorize}: cualquier miembro autenticado de la clínica puede buscar diagnósticos
 * (design.md D10).
 */
@RestController
@RequestMapping("/cie10")
@Tag(name = "clinical-history", description = "Historia clínica y catálogo CIE10")
public class Cie10Controller {

    private final Cie10SearchService searchService;

    public Cie10Controller(Cie10SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<Cie10SearchResultResponse> search(@RequestParam String query) {
        return searchService.search(query).stream().map(Cie10SearchResultResponse::from).toList();
    }
}
