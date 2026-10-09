package com.odontorisas.presentation.controller;

import com.odontorisas.common.FindingType;
import com.odontorisas.presentation.dto.DentalFindingCatalogEntryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

/**
 * El catálogo cerrado de 38 hallazgos del odontograma (NTS 188 6.1.1-6.1.38), aplanado por par
 * {@code (type, subtype)} (design.md D6/`4.1`). Global, no cuelga de un paciente: es el mismo
 * catálogo para toda la clínica. Fuente única: el enum {@link FindingType} — el código no lee la
 * tabla {@code dental_finding_catalog}, que es la fuente de <b>integridad</b>, no de <b>tipos</b>.
 */
@RestController
@RequestMapping("/dental-chart/catalog")
@Tag(name = "dental-chart", description = "Odontograma inicial: bloque y hallazgos")
public class DentalFindingCatalogController {

    @GetMapping
    public List<DentalFindingCatalogEntryResponse> catalog() {
        return Stream.of(FindingType.values())
            .flatMap(this::rows)
            .toList();
    }

    private Stream<DentalFindingCatalogEntryResponse> rows(FindingType type) {
        if (!type.hasSubtypes()) {
            return Stream.of(new DentalFindingCatalogEntryResponse(
                type, "", type.acronym(), type.scope(), type.colorBehavior(),
                type.requiresGrade(), type.requiresSpecification()));
        }
        return type.subtypes().stream().map(subtype -> new DentalFindingCatalogEntryResponse(
            type, subtype.code(), subtype.acronym(), type.scope(), type.colorBehavior(),
            type.requiresGrade(), type.requiresSpecification()));
    }
}
