package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.BusinessHoursResponse;
import com.odontorisas.presentation.dto.UpdateBusinessHoursRequest;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.appointment.BusinessHoursService;
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
 * Horario de atención de la organización activa (add-appointments design.md D5). La organización es siempre la
 * del token; ninguna ruta ni DTO lleva su id. Lo lee cualquier miembro; lo reemplaza solo el {@code ADMIN}.
 */
@RestController
@RequestMapping("/business-hours")
@Tag(name = "business-hours", description = "Horario de atención de la clínica")
public class BusinessHoursController {

    private final BusinessHoursService businessHoursService;

    public BusinessHoursController(BusinessHoursService businessHoursService) {
        this.businessHoursService = businessHoursService;
    }

    @GetMapping
    public BusinessHoursResponse get() {
        return BusinessHoursResponse.from(businessHoursService.current());
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Horario reemplazado.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = BusinessHoursResponse.class))),
        @ApiResponse(responseCode = "400", description = "Tramo fuera de la grilla de 5 minutos, que no abre antes de "
            + "cerrar, solapado, o semana incompleta o con días repetidos. No se guarda nada.",
            content = @Content(mediaType = "application/problem+json",
                schema = @Schema(implementation = ValidationProblem.class)))
    })
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessHoursResponse replace(@Valid @RequestBody UpdateBusinessHoursRequest request) {
        return BusinessHoursResponse.from(businessHoursService.replace(request.toBusinessHours()));
    }
}
