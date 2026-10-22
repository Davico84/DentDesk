package com.odontorisas.service.clinicalhistory;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Códigos CIE10 sugeridos para un hallazgo del odontograma (update-consultation-note-findings
 * design.md D4). El mapa lo validó el odontólogo (explore 2026-10-16) y cubre solo los hallazgos
 * patológicos siempre rojos; el resto no sugiere nada y se busca en el catálogo. Una sugerencia no
 * decide: el código final lo elige el usuario.
 *
 * <p>La descripción sale del catálogo, no del mapa; un test exige que cada código exista en él.
 */
@Service
public class Cie10SuggestionService {

    /** Clave {@code TIPO:SUBTIPO}, o {@code TIPO} cuando vale para todos sus subtipos. */
    static final Map<String, List<String>> SUGGESTIONS = Map.of(
        "LESION_CARIES:MB", List.of("K020"),
        "LESION_CARIES:CE", List.of("K020"),
        "LESION_CARIES:CD", List.of("K021"),
        "LESION_CARIES:CDP", List.of("K025", "K040"),
        "FRACTURA_DENTAL", List.of("S025"),
        "DEFECTOS_DESARROLLO_ESMALTE:O", List.of("K003", "K004"),
        "DEFECTOS_DESARROLLO_ESMALTE:PE", List.of("K004"),
        "MOVILIDAD_PATOLOGICA", List.of("K053", "K088"),
        "REMANENTE_RADICULAR", List.of("K083"),
        "SUPERFICIE_DESGASTADA", List.of("K030", "K031", "K032"));

    private final Cie10SearchService searchService;

    public Cie10SuggestionService(Cie10SearchService searchService) {
        this.searchService = searchService;
    }

    public List<Cie10SearchResult> suggest(String findingType, String subtype) {
        String sub = subtype == null ? "" : subtype;
        List<String> codes = Optional.ofNullable(SUGGESTIONS.get(findingType + ":" + sub))
            .or(() -> Optional.ofNullable(SUGGESTIONS.get(findingType)))
            .orElse(List.of());
        return codes.stream().map(searchService::find).flatMap(Optional::stream).toList();
    }

    /** Todos los códigos del mapa, para verificarlos contra el catálogo. */
    static List<String> allSuggestedCodes() {
        return SUGGESTIONS.values().stream().flatMap(List::stream).distinct().toList();
    }
}
