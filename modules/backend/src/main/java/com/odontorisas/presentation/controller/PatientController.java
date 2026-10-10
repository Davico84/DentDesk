package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.PageResponse;
import com.odontorisas.presentation.dto.PatientRequest;
import com.odontorisas.presentation.dto.PatientResponse;
import com.odontorisas.presentation.dto.PatientSummaryResponse;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.patient.PatientData;
import com.odontorisas.service.patient.PatientSearch;
import com.odontorisas.service.patient.PatientService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de la ficha del paciente en la clínica activa. <b>Sin {@code @PreAuthorize} de rol</b>
 * (design.md, docs/domain.md): los tres roles (ADMIN, DENTIST, RECEPTIONIST) gestionan pacientes,
 * así que basta con estar autenticado en la organización. Por eso el customizer del contrato no
 * inyecta un {@code 403} a estos endpoints — solo el {@code 401} y el {@code 500}.
 *
 * <p>El listado usa {@code GET} + query params (ADR-0002), nunca el método {@code QUERY}.
 * El {@code organizationId} <b>no aparece en ninguna firma</b>: lo pone el contexto de tenant.
 */
@RestController
@RequestMapping("/patients")
@Tag(name = "patients", description = "Ficha clínico-administrativa de los pacientes de la clínica")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Paciente registrado.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PatientResponse.class))),
        @ApiResponse(responseCode = "400", description = "Faltan datos mínimos, o falta el responsable de un menor.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "409", description = "El documento ya está registrado en esta clínica.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public PatientResponse create(@Valid @RequestBody PatientRequest request) {
        return PatientResponse.from(patientService.create(toData(request)));
    }

    @GetMapping("/{id}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "La ficha del paciente.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PatientResponse.class))),
        @ApiResponse(responseCode = "404", description = "No existe en la clínica activa (tampoco se revela si existe en otra).",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public PatientResponse get(@PathVariable Long id) {
        return PatientResponse.from(patientService.getById(id));
    }

    @PutMapping("/{id}")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ficha actualizada.",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PatientResponse.class))),
        @ApiResponse(responseCode = "400", description = "Datos inválidos, o falta el responsable de un menor.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "No existe en la clínica activa.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "El documento ya lo tiene otro paciente de la clínica.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public PatientResponse update(@PathVariable Long id, @Valid @RequestBody PatientRequest request) {
        return PatientResponse.from(patientService.update(id, toData(request)));
    }

    @PostMapping("/{id}/deactivate")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paciente dado de baja (archivado, no borrado).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PatientResponse.class))),
        @ApiResponse(responseCode = "404", description = "No existe en la clínica activa.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public PatientResponse deactivate(@PathVariable Long id) {
        return PatientResponse.from(patientService.deactivate(id));
    }

    @PostMapping("/{id}/reactivate")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Paciente reactivado (vuelve al listado por defecto).",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PatientResponse.class))),
        @ApiResponse(responseCode = "404", description = "No existe en la clínica activa.",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    })
    public PatientResponse reactivate(@PathVariable Long id) {
        return PatientResponse.from(patientService.reactivate(id));
    }

    @GetMapping
    public PageResponse<PatientSummaryResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.of(
            patientService.search(new PatientSearch(query, includeInactive), pageable)
                .map(PatientSummaryResponse::from));
    }

    /** El controller traduce el DTO de la API al tipo del servicio (design.md D6). */
    private static PatientData toData(PatientRequest r) {
        return new PatientData(
            r.fullName(), r.documentType(), r.document(), r.birthDate(), r.sex(),
            r.phone(), r.email(), r.address(),
            r.guardianName(), r.guardianRelationship(), r.guardianDocument(), r.guardianPhone(),
            r.emergencyContactName(), r.emergencyContactPhone(), r.emergencyContactRelationship(),
            r.allergies(), r.conditions(), r.currentMedication());
    }
}
