package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.LoginRequest;
import com.odontorisas.presentation.dto.PlatformAuthResponse;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.platform.PlatformAuthService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login global. Emite un access token sin {@code oid} que solo vale en {@code /platform/**}; no hay
 * refresh token ni cookie: la sesión de plataforma dura lo que el access token.
 */
@RestController
@RequestMapping("/platform/auth")
@Tag(name = "platform", description = "Administración global de plataforma")
public class PlatformAuthController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final PlatformAuthService service;

    public PlatformAuthController(PlatformAuthService service) {
        this.service = service;
    }

    /** El 401 es semántico (credenciales inválidas o bloqueo): la ruta es pública, se declara aquí. */
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sesión de plataforma iniciada.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = PlatformAuthResponse.class))),
        @ApiResponse(responseCode = "400", description = "Campos inválidos.",
            content = @Content(mediaType = PROBLEM_JSON,
                schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "401",
            description = "Correo o contraseña incorrectos, la identidad no es administradora de plataforma, "
                + "o está bloqueada temporalmente por intentos fallidos.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PostMapping("/login")
    public PlatformAuthResponse platformLogin(@Valid @RequestBody LoginRequest request) {
        return new PlatformAuthResponse(service.login(request.email(), request.password()));
    }
}
