package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.ApiProblem;
import com.odontorisas.presentation.dto.InvitationRequest;
import com.odontorisas.presentation.dto.InvitationResponse;
import com.odontorisas.presentation.dto.MemberClinicalCapacityRequest;
import com.odontorisas.presentation.dto.MemberResponse;
import com.odontorisas.presentation.dto.MemberStatusRequest;
import com.odontorisas.presentation.dto.StaffInvitationResponse;
import com.odontorisas.presentation.dto.ValidationProblem;
import com.odontorisas.service.staff.StaffService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestión del personal por el ADMIN de la organización activa (capacidad {@code staff}). Todo se resuelve contra
 * la organización del token; un recurso de otra clínica responde 404. El 401 y el 403 de rol los inyecta
 * {@code OpenApiErrorsConfig}.
 */
@RestController
@RequestMapping("/staff")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "staff", description = "Gestión del personal de la clínica")
public class StaffController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final StaffService staffService;

    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invitación emitida y enviada por correo (sin el token).",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = InvitationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Campos inválidos.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "409",
            description = "El correo ya es miembro (`/errors/member-already-exists`) o ya tiene una invitación "
                + "pendiente o vencida (`/errors/invitation-already-pending`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "503", description = "No se pudo enviar el correo; no se crea la invitación.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PostMapping("/invitations")
    public InvitationResponse invite(@Valid @RequestBody InvitationRequest request) {
        return InvitationResponse.from(staffService.invite(request.email(), request.role()));
    }

    @ApiResponse(responseCode = "200", description = "Invitaciones de la clínica, más recientes primero.",
        content = @Content(mediaType = "application/json",
            array = @ArraySchema(schema = @Schema(implementation = StaffInvitationResponse.class))))
    @GetMapping("/invitations")
    public List<StaffInvitationResponse> invitations() {
        return staffService.invitations().stream().map(StaffInvitationResponse::from).toList();
    }

    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Enlace y código nuevos enviados; los anteriores quedan anulados.",
            content = @Content(mediaType = "application/json",
                schema = @Schema(implementation = StaffInvitationResponse.class))),
        @ApiResponse(responseCode = "404", description = "La invitación no existe en la clínica (`/errors/invitation-not-found`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La invitación ya fue aceptada o revocada (`/errors/invitation-not-pending`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "503", description = "No se pudo enviar el correo; la invitación queda como estaba.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PostMapping("/invitations/{id}/resend")
    public StaffInvitationResponse resendInvitation(@PathVariable Long id) {
        return StaffInvitationResponse.from(staffService.resend(id));
    }

    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Invitación revocada; su enlace deja de funcionar."),
        @ApiResponse(responseCode = "404", description = "La invitación no existe en la clínica (`/errors/invitation-not-found`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "La invitación ya fue aceptada o revocada (`/errors/invitation-not-pending`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @DeleteMapping("/invitations/{id}")
    public ResponseEntity<Void> revoke(@PathVariable Long id) {
        staffService.revoke(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/members")
    public List<MemberResponse> members() {
        return staffService.members().stream().map(MemberResponse::from).toList();
    }

    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Estado actualizado."),
        @ApiResponse(responseCode = "400", description = "Estado inválido.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "La persona no es miembro de la clínica (`/errors/member-not-found`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class))),
        @ApiResponse(responseCode = "409", description = "No se puede cambiar el estado de la propia cuenta (`/errors/cannot-change-own-status`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PatchMapping("/members/{id}/status")
    public ResponseEntity<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody MemberStatusRequest request) {
        staffService.changeStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Capacidad clínica retirada o restituida. Restituir solo levanta "
            + "el retiro: la capacidad sigue dependiendo de la credencial del profesional."),
        @ApiResponse(responseCode = "400", description = "Cuerpo inválido.",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ValidationProblem.class))),
        @ApiResponse(responseCode = "404", description = "La persona no es miembro de la clínica (`/errors/member-not-found`).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ApiProblem.class)))
    })
    @PatchMapping("/members/{id}/clinical-capacity")
    public ResponseEntity<Void> changeClinicalCapacity(@PathVariable Long id,
                                                       @Valid @RequestBody MemberClinicalCapacityRequest request) {
        staffService.changeClinicalCapacity(id, request.revoked());
        return ResponseEntity.noContent().build();
    }
}
