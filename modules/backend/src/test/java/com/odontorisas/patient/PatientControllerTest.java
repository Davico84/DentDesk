package com.odontorisas.patient;

import com.odontorisas.common.DocumentType;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.infra.security.JwtAuthenticationFilter;
import com.odontorisas.presentation.controller.PatientController;
import com.odontorisas.service.patient.DocumentAlreadyExistsException;
import com.odontorisas.service.patient.PatientNotFoundException;
import com.odontorisas.service.patient.PatientService;
import com.odontorisas.service.patient.PatientSummary;
import com.odontorisas.service.patient.PatientView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice de <b>controller</b> (docs/testing.md §5, hueco cerrado): request/response, validación y
 * status codes de {@code PatientController} con el servicio <b>mockeado</b> — sin BD, sin
 * Testcontainers, sin contexto de tenant. Es complementaria a {@link PatientControllerIT} (HTTP
 * contra PostgreSQL real): esta prueba rápida cubre la <b>forma</b> de la API; la IT cubre el
 * comportamiento de negocio de punta a punta (aislamiento, conflictos reales).
 *
 * <p>{@code addFilters = false}: {@code PatientController} no lleva {@code @PreAuthorize} (todo
 * rol autenticado puede gestionar pacientes) — la seguridad de este endpoint no es de rol, así
 * que no aporta nada activar el filtro JWT real en un slice que no lo carga de todas formas.
 */
@WebMvcTest(controllers = PatientController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    private PatientView aView(Long id, String fullName) {
        return new PatientView(id, fullName, DocumentType.DNI, "10000001", LocalDate.of(1990, 1, 1),
            false, null, null, null, null, null, null, null, null, null, null, null,
            List.of(), List.of(), List.of(), PatientStatus.ACTIVE, false, false);
    }

    @Test
    void create_returns_201_with_the_created_patient() throws Exception {
        given(patientService.create(any())).willReturn(aView(1L, "Ada Lovelace"));

        mockMvc.perform(post("/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Ada Lovelace\",\"birthDate\":\"1990-01-01\",\"phone\":\"+51988877766\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
            .andExpect(jsonPath("$.minor").value(false));
    }

    @Test
    void create_without_full_name_returns_400_validation_problem() throws Exception {
        mockMvc.perform(post("/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"birthDate\":\"1990-01-01\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[*].field").value(hasItem("fullName")));

        verifyNoInteractions(patientService);
    }

    @Test
    void get_unknown_id_returns_404_as_problem_detail() throws Exception {
        given(patientService.getById(anyLong())).willThrow(new PatientNotFoundException());

        mockMvc.perform(get("/patients/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("/errors/patient-not-found"));
    }

    @Test
    void create_with_duplicate_document_returns_409_identifying_the_existing_patient() throws Exception {
        given(patientService.create(any())).willThrow(new DocumentAlreadyExistsException(7L));

        mockMvc.perform(post("/patients")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Otro\",\"birthDate\":\"1990-01-01\",\"phone\":\"+51988877766\","
                    + "\"documentType\":\"DNI\",\"document\":\"10000001\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.type").value("/errors/patient-document-taken"))
            .andExpect(jsonPath("$.existingPatientId").value(7));
    }

    @Test
    void search_returns_a_page_of_summaries() throws Exception {
        PatientSummary summary = new PatientSummary(1L, "Grace Hopper", "GH-1",
            LocalDate.of(1980, 1, 1), false, "+51987654321", PatientStatus.ACTIVE);
        Page<PatientSummary> page = new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1);
        given(patientService.search(any(), any())).willReturn(page);

        mockMvc.perform(get("/patients").param("query", "Grace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].fullName").value("Grace Hopper"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }
}
