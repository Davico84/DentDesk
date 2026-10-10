package com.odontorisas.clinicalhistory;

import com.odontorisas.AbstractIntegrationTest;
import com.odontorisas.service.clinicalhistory.Cie10SearchResult;
import com.odontorisas.service.clinicalhistory.Cie10SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Búsqueda del catálogo CIE10 contra los datos reales cargados por {@code Cie10CatalogLoader}
 * (spec: búsqueda jerárquica, design.md D6).
 */
@SpringBootTest
class Cie10SearchServiceIT extends AbstractIntegrationTest {

    @Autowired
    private Cie10SearchService searchService;

    @Test
    void a_search_by_description_returns_its_full_hierarchical_path() {
        List<Cie10SearchResult> results = searchService.search("COLERA");

        assertThat(results).isNotEmpty();
        Cie10SearchResult category = results.stream()
            .filter(r -> r.code().equals("A00"))
            .findFirst().orElseThrow();

        // Ruta completa: capítulo → grupo → categoría (A00 no tiene padre más allá de su grupo).
        assertThat(category.path()).extracting(Cie10SearchResult.Cie10PathSegment::nivel)
            .containsExactly("CAPITULO", "GRUPO", "CATEGORIA");
        assertThat(category.path().get(0).descripcion()).contains("CAPITULO I");
        assertThat(category.path().getLast().code()).isEqualTo("A00");
    }

    @Test
    void a_subcategory_result_includes_all_four_levels_in_its_path() {
        List<Cie10SearchResult> results = searchService.search("VIBRIO CHOLERAE 01, BIOTIPO CHOLERAE");

        Cie10SearchResult subcategory = results.stream()
            .filter(r -> r.code().equals("A000"))
            .findFirst().orElseThrow();

        assertThat(subcategory.path()).extracting(Cie10SearchResult.Cie10PathSegment::nivel)
            .containsExactly("CAPITULO", "GRUPO", "CATEGORIA", "SUBCATEGORIA");
    }

    @Test
    void an_existing_code_is_reported_as_existing() {
        assertThat(searchService.exists("A00")).isTrue();
        assertThat(searchService.exists("A000")).isTrue();
    }

    @Test
    void a_nonexistent_code_is_reported_as_not_existing() {
        assertThat(searchService.exists("ZZZZ")).isFalse();
    }
}
