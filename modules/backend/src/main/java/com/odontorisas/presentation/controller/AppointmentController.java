package com.odontorisas.presentation.controller;

import com.odontorisas.presentation.dto.AppointmentResponse;
import com.odontorisas.presentation.dto.CancelAppointmentRequest;
import com.odontorisas.presentation.dto.RescheduleAppointmentRequest;
import com.odontorisas.presentation.dto.SchedulableDentistResponse;
import com.odontorisas.presentation.dto.ScheduleAppointmentRequest;
import com.odontorisas.service.appointment.AppointmentService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

/**
 * La agenda de la organización activa (add-appointments design.md D8). Cualquier miembro agenda y gestiona
 * citas; "atender con nota" exige además capacidad clínica a quien llama. Los errores salen en RFC 9457.
 */
@RestController
@RequestMapping("/appointments")
@Tag(name = "appointments", description = "Agenda interna de la clínica")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /** Citas cuyo inicio cae en los días {@code from..to} de la clínica (inclusive, máximo 7). */
    @GetMapping
    public List<AppointmentResponse> listAppointments(
            @Parameter(description = "Primer día, en la zona de la clínica", example = "2030-12-02")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Último día (inclusive), en la zona de la clínica", example = "2030-12-08")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Solo las citas de este odontólogo")
            @RequestParam(required = false) Long dentistId) {
        return appointmentService.list(from, to, dentistId).stream().map(AppointmentResponse::from).toList();
    }

    @GetMapping("/dentists")
    public List<SchedulableDentistResponse> listSchedulableDentists() {
        return appointmentService.schedulableDentists().stream().map(SchedulableDentistResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AppointmentResponse getAppointment(@PathVariable Long id) {
        return AppointmentResponse.from(appointmentService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse scheduleAppointment(@Valid @RequestBody ScheduleAppointmentRequest request) {
        return AppointmentResponse.from(appointmentService.schedule(request.toData()));
    }

    @PutMapping("/{id}/schedule")
    public AppointmentResponse rescheduleAppointment(@PathVariable Long id, @Valid @RequestBody RescheduleAppointmentRequest request) {
        return AppointmentResponse.from(appointmentService.reschedule(id, request.toData()));
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancelAppointment(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
        return AppointmentResponse.from(appointmentService.cancel(id, request == null ? null : request.reason()));
    }

    @PostMapping("/{id}/attended")
    public AppointmentResponse markAppointmentAttended(@PathVariable Long id) {
        return AppointmentResponse.from(appointmentService.markAttended(id));
    }

    @PostMapping("/{id}/no-show")
    public AppointmentResponse markAppointmentNoShow(@PathVariable Long id) {
        return AppointmentResponse.from(appointmentService.markNoShow(id));
    }

    /** Abre la nota de consulta del paciente enlazada a la cita, que queda ATENDIDA (design.md D7). */
    @PostMapping("/{id}/consultation-note")
    public AppointmentResponse attendAppointmentWithNote(@PathVariable Long id) {
        return AppointmentResponse.from(appointmentService.attendWithNote(id));
    }
}
