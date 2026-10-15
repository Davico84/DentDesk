package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.ClinicalHistoryRequest;
import com.odontorisas.presentation.dto.ClinicalHistoryResponse;
import com.odontorisas.presentation.dto.DiagnosisRequest;
import com.odontorisas.presentation.dto.DiagnosisResponse;
import com.odontorisas.presentation.dto.EnablementResponse;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.admission.CloseAdmissionService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryDiagnosisService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryData;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryView;
import com.odontorisas.service.clinicalhistory.DiagnosisData;
import com.odontorisas.service.patient.PatientService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historia Clínica: documento inicial obligatorio antes de tratar (ADR-0011). Todas las rutas
 * cuelgan del paciente ({@code patientId}), no de un id propio de la historia — hay como mucho
 * una por paciente (spec: única por paciente y clínica). <b>Sin {@code @PreAuthorize}</b>
 * (design.md D10): cualquier miembro autenticado de la clínica opera; el candado por rol
 * (perfil profesional y habilitación) llega con `staff`.
 */
@RestController
@RequestMapping("/patients/{patientId}/clinical-history")
@Tag(name = "clinical-history", description = "Historia clínica y catálogo CIE10")
public class ClinicalHistoryController {

    private final ClinicalHistoryService clinicalHistoryService;
    private final ClinicalHistoryDiagnosisService diagnosisService;
    private final PatientService patientService;
    private final CloseAdmissionService closeAdmissionService;

    public ClinicalHistoryController(ClinicalHistoryService clinicalHistoryService,
                                      ClinicalHistoryDiagnosisService diagnosisService,
                                      PatientService patientService,
                                      CloseAdmissionService closeAdmissionService) {
        this.clinicalHistoryService = clinicalHistoryService;
        this.diagnosisService = diagnosisService;
        this.patientService = patientService;
        this.closeAdmissionService = closeAdmissionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Historia Clínica creada en DRAFT.",
            content = @Content(schema = @Schema(implementation = ClinicalHistoryResponse.class))),
        @ApiResponse(responseCode = "404", description = "No existe el paciente en la clínica activa.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "El paciente ya tiene una Historia Clínica.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public ClinicalHistoryResponse create(@PathVariable Long patientId) {
        return toResponse(clinicalHistoryService.create(patientId), patientId);
    }

    @GetMapping
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "La Historia Clínica del paciente.",
            content = @Content(schema = @Schema(implementation = ClinicalHistoryResponse.class))),
        @ApiResponse(responseCode = "404", description = "El paciente no tiene Historia Clínica en la clínica activa.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public ClinicalHistoryResponse get(@PathVariable Long patientId) {
        return toResponse(clinicalHistoryService.getByPatientId(patientId), patientId);
    }

    @PutMapping
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Guardado incremental en DRAFT.",
            content = @Content(schema = @Schema(implementation = ClinicalHistoryResponse.class))),
        @ApiResponse(responseCode = "404", description = "El paciente no tiene Historia Clínica.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La Historia ya está sellada (COMPLETED).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public ClinicalHistoryResponse update(@PathVariable Long patientId, @RequestBody ClinicalHistoryRequest request) {
        return toResponse(clinicalHistoryService.update(patientId, toData(request)), patientId);
    }

    /**
     * Sella la Historia Clínica <b>y</b> el odontograma inicial, en una sola operación (ADR-0012
     * D5, `add-dental-chart`): la Historia ya no se sella sola. La ruta se conserva; la semántica
     * cambia — delega en {@link CloseAdmissionService} en vez de llamar a {@code
     * clinicalHistoryService.complete(...)} directo (design.md D4).
     */
    @PostMapping("/complete")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historia y odontograma sellados atómicamente.",
            content = @Content(schema = @Schema(implementation = ClinicalHistoryResponse.class))),
        @ApiResponse(responseCode = "400", description = "Falta un ítem del checklist mínimo, de cualquiera de los dos documentos.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La Historia o el odontograma ya están sellados (transición inválida).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public ClinicalHistoryResponse complete(@PathVariable Long patientId) {
        return toResponse(closeAdmissionService.close(patientId).clinicalHistory(), patientId);
    }

    @GetMapping("/enablement")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Estado de habilitación para tratamientos.",
            content = @Content(schema = @Schema(implementation = EnablementResponse.class)))
    })
    public EnablementResponse enablement(@PathVariable Long patientId) {
        return EnablementResponse.from(clinicalHistoryService.getEnablementStatus(patientId));
    }

    @PostMapping("/diagnoses")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Diagnóstico agregado.",
            content = @Content(schema = @Schema(implementation = DiagnosisResponse.class))),
        @ApiResponse(responseCode = "400", description = "Código CIE10 inexistente, o datos inválidos.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "409", description = "La Historia ya está sellada (COMPLETED).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public DiagnosisResponse addDiagnosis(@PathVariable Long patientId, @Valid @RequestBody DiagnosisRequest request) {
        return DiagnosisResponse.from(diagnosisService.add(patientId, toData(request)));
    }

    @PutMapping("/diagnoses/{diagnosisId}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Diagnóstico actualizado.",
            content = @Content(schema = @Schema(implementation = DiagnosisResponse.class))),
        @ApiResponse(responseCode = "404", description = "No existe ese diagnóstico en la Historia.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La Historia ya está sellada (COMPLETED).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public DiagnosisResponse updateDiagnosis(@PathVariable Long patientId, @PathVariable Long diagnosisId,
                                              @Valid @RequestBody DiagnosisRequest request) {
        return DiagnosisResponse.from(diagnosisService.update(patientId, diagnosisId, toData(request)));
    }

    @DeleteMapping("/diagnoses/{diagnosisId}")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Diagnóstico eliminado."),
        @ApiResponse(responseCode = "404", description = "No existe ese diagnóstico en la Historia.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La Historia ya está sellada (COMPLETED).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDiagnosis(@PathVariable Long patientId, @PathVariable Long diagnosisId) {
        diagnosisService.remove(patientId, diagnosisId);
    }

    private ClinicalHistoryResponse toResponse(ClinicalHistoryView view, Long patientId) {
        var patient = patientService.getById(patientId);
        var diagnoses = diagnosisService.listByPatient(patientId).stream()
            .map(DiagnosisResponse::from)
            .toList();
        return ClinicalHistoryResponse.from(view, patient, diagnoses);
    }

    private static ClinicalHistoryData toData(ClinicalHistoryRequest r) {
        return new ClinicalHistoryData(
            r.motivoConsulta(), r.enfermedadActual(), r.antecedentesPersonales(), r.examenClinico(),
            r.pronostico(), r.signosVitales(),
            Boolean.TRUE.equals(r.alergiasRevisado()), r.alergiasDetalle(),
            Boolean.TRUE.equals(r.medicacionRevisado()), r.medicacionDetalle(),
            Boolean.TRUE.equals(r.antecedentesFamiliaresRevisado()), r.antecedentesFamiliaresDetalle());
    }

    private static DiagnosisData toData(DiagnosisRequest r) {
        return new DiagnosisData(r.cie10Code(), r.ubicacion(), r.fdiNumber(), r.tipoDiagnostico());
    }
}
