package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.ProfessionalCredentialRequest;
import com.odontorisas.presentation.dto.ProfessionalCredentialResponse;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.staff.ProfessionalCredentialService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Credencial profesional del usuario de la sesión ({@code update-staff-professional-credential} design.md
 * D6). Cualquier miembro de una clínica la declara para sí mismo; el usuario sale del token, nunca de la
 * petición. Una sesión de plataforma ni siquiera llega acá: el filtro la deja sin autenticar fuera de
 * {@code /platform/**}.
 */
@RestController
@RequestMapping("/me/professional-credential")
@PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
@Tag(name = "staff", description = "Gestión del personal de la clínica")
public class ProfessionalCredentialController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final ProfessionalCredentialService credentialService;

    public ProfessionalCredentialController(ProfessionalCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @ApiResponse(responseCode = "200",
        description = "La credencial propia (o null) y la capacidad clínica en la clínica activa (null sin clínica activa).",
        content = @Content(mediaType = "application/json",
            schema = @Schema(implementation = ProfessionalCredentialResponse.class)))
    @GetMapping
    public ProfessionalCredentialResponse mine() {
        return ProfessionalCredentialResponse.from(credentialService.mine());
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credencial declarada o renovada.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ProfessionalCredentialResponse.class))),
        @ApiResponse(responseCode = "400",
            description = "Campos inválidos, declaración jurada sin aceptar, o fecha anterior a hoy en la hora de Perú "
                + "(`/errors/licensed-until-in-past`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "409",
            description = "El número ya está en la credencial de otra cuenta (`/errors/credential-number-taken`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PutMapping
    public ProfessionalCredentialResponse declare(@Valid @RequestBody ProfessionalCredentialRequest request) {
        return ProfessionalCredentialResponse.from(
            credentialService.declare(request.copNumber(), request.licensedUntil()));
    }
}
