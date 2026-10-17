package com.odontorisas.service.clinicalhistory;

import com.odontorisas.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenarios de <i>Diagnóstico propuesto desde un hallazgo atendido</i> del lado del servidor
 * (update-consultation-note-findings design.md D4): qué sugiere cada hallazgo y que toda sugerencia
 * exista en el catálogo CIE10 cargado. En el paquete del servicio para leer el mapa completo.
 */
@SpringBootTest
class Cie10SuggestionServiceIT extends AbstractIntegrationTest {

    @Autowired
    private Cie10SuggestionService suggestionService;
    @Autowired
    private Cie10SearchService searchService;

    @Test
    void caries_in_dentin_suggests_K021() {
        assertThat(suggestionService.suggest("LESION_CARIES", "CD"))
            .extracting(Cie10SearchResult::code).containsExactly("K021");
        assertThat(suggestionService.suggest("LESION_CARIES", "CD").get(0).descripcion()).containsIgnoringCase("dentina");
    }

    @Test
    void a_type_without_subtype_key_uses_the_type_wide_entry() {
        assertThat(suggestionService.suggest("SUPERFICIE_DESGASTADA", ""))
            .extracting(Cie10SearchResult::code).containsExactly("K030", "K031", "K032");
    }

    @Test
    void a_finding_without_suggestions_returns_an_empty_list() {
        assertThat(suggestionService.suggest("CORONA", "CM")).isEmpty();
        assertThat(suggestionService.suggest("RESTAURACION_TEMPORAL", "")).isEmpty();
        assertThat(suggestionService.suggest("NO_EXISTE", null)).isEmpty();
    }

    @Test
    void every_suggested_code_exists_in_the_cie10_catalog() {
        assertThat(Cie10SuggestionService.allSuggestedCodes())
            .isNotEmpty()
            .allSatisfy(code -> assertThat(searchService.exists(code)).as("%s en cie10_code", code).isTrue());
    }
}
