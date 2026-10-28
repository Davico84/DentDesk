package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.ProfileRequest;
import com.odontorisas.presentation.dto.ProfileResponse;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.auth.ProfileService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil propio ({@code update-authentication-my-profile} design.md D1). Sin {@code id} en la ruta ni en el
 * cuerpo: siempre es el usuario de la sesión. Una sesión de plataforma no llega acá: el filtro la deja sin
 * autenticar fuera de {@code /platform/**}. El 401 de sesión ausente lo agrega el customizer del contrato.
 */
@RestController
@RequestMapping("/me/profile")
@PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
@Tag(name = "auth", description = "Autenticación")
public class ProfileController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @ApiResponse(responseCode = "200", description = "El perfil propio.",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProfileResponse.class)))
    @GetMapping
    public ProfileResponse myProfile() {
        return ProfileResponse.from(profileService.mine());
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nombre corregido; devuelve el perfil actualizado.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = ProfileResponse.class))),
        @ApiResponse(responseCode = "400",
            description = "Nombre vacío, o de menos de 2 o más de 120 caracteres sin contar los espacios de los extremos.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ValidationProblem.class)))
    })
    @PatchMapping
    public ProfileResponse updateMyProfile(@Valid @RequestBody ProfileRequest request) {
        return ProfileResponse.from(profileService.updateFullName(request.fullName()));
    }
}
