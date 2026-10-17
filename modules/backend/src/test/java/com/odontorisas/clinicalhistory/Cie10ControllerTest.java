package com.odontorisas.clinicalhistory;

import com.odontorisas.infra.security.JwtAuthenticationFilter;
import com.odontorisas.presentation.controller.Cie10Controller;
import com.odontorisas.service.clinicalhistory.Cie10SearchResult;
import com.odontorisas.service.clinicalhistory.Cie10SearchResult.Cie10PathSegment;
import com.odontorisas.service.clinicalhistory.Cie10SearchService;
import com.odontorisas.service.clinicalhistory.Cie10SuggestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice de controller: forma de la respuesta de búsqueda CIE10 (spec: búsqueda jerárquica). */
@WebMvcTest(controllers = Cie10Controller.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class Cie10ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Cie10SearchService searchService;

    /** Las sugerencias por hallazgo se prueban contra el catálogo real en {@code Cie10SuggestionServiceIT}. */
    @MockitoBean
    private Cie10SuggestionService suggestionService;

    @Test
    void search_returns_results_with_their_hierarchical_path() throws Exception {
        given(searchService.search("colera")).willReturn(List.of(
            new Cie10SearchResult("A00", "COLERA", "CATEGORIA", List.of(
                new Cie10PathSegment("CAP01", "CAPITULO I: ...", "CAPITULO"),
                new Cie10PathSegment("GRP0101", "(A00 - A09) ...", "GRUPO"),
                new Cie10PathSegment("A00", "COLERA", "CATEGORIA")))));

        mockMvc.perform(get("/cie10").param("query", "colera"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].code").value("A00"))
            .andExpect(jsonPath("$[0].path.length()").value(3))
            .andExpect(jsonPath("$[0].path[0].nivel").value("CAPITULO"))
            .andExpect(jsonPath("$[0].path[2].code").value("A00"));
    }
}
