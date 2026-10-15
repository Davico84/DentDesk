package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.PlatformOrganizationRequest;
import com.odontorisas.presentation.dto.PlatformOrganizationResponse;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.platform.PlatformOrganizationService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aprovisionamiento global de clínicas. El 401 y el 403 los inyecta {@code OpenApiErrorsConfig}. */
@RestController
@RequestMapping("/platform/organizations")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@Tag(name = "platform", description = "Administración global de plataforma")
public class PlatformOrganizationController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final PlatformOrganizationService service;

    public PlatformOrganizationController(PlatformOrganizationService service) {
        this.service = service;
    }

    /**
     * Devuelve <b>201</b>: crea la organización, su configuración y la invitación inicial
     * {@code ADMIN}. La respuesta no lleva el token: viaja solo en el correo de la invitación.
     */
    @ApiResponses({
        @ApiResponse(responseCode = "201",
            description = "Organización creada; invitación ADMIN emitida y enviada por correo.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = PlatformOrganizationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Campos inválidos (ver `errors` para el detalle por campo).",
            content = @Content(mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "409",
            description = "El correo del administrador ya pertenece a una identidad "
                + "(`/errors/email-already-exists`), o una carrera tomó el mismo slug "
                + "(`/errors/organization-slug-taken`). No se crea nada.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "503",
            description = "No se pudo entregar el correo de invitación (`/errors/invitation-delivery-failed`). "
                + "La operación se revierte entera; se puede reintentar.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PostMapping
    public ResponseEntity<PlatformOrganizationResponse> provisionOrganization(
            @Valid @RequestBody PlatformOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PlatformOrganizationResponse.from(
            service.provision(request.organizationName(), request.adminEmail())));
    }
}
