package com.odontorisas.service.appointment;

import com.odontorisas.common.ClinicalCapacityState;
import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * El odontólogo elegido no puede atender esa cita (409, add-appointments design.md D4): no es miembro de la
 * clínica activa, o no tiene capacidad clínica vigente en la fecha de la cita. No es la
 * {@code ClinicalCapacityRequiredException} (403): quien llama sí está autorizado; el conflicto es del dato.
 */
public class DentistNotSchedulableException extends BusinessException {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** {@code null}: el usuario no es miembro de la clínica activa. */
    private final ClinicalCapacityState reason;
    private final LocalDate expiredOn;

    public DentistNotSchedulableException(ClinicalCapacityState reason, LocalDate licensedUntil) {
        super(message(reason, licensedUntil), HttpStatus.CONFLICT, "dentist-not-schedulable");
        this.reason = reason;
        this.expiredOn = reason == ClinicalCapacityState.EXPIRED ? licensedUntil : null;
    }

    /** El usuario no pertenece a la clínica activa. */
    public static DentistNotSchedulableException notAMember() {
        return new DentistNotSchedulableException(null, null);
    }

    private static String message(ClinicalCapacityState reason, LocalDate licensedUntil) {
        if (reason == null) {
            return "No es un odontólogo agendable en esta clínica.";
        }
        return switch (reason) {
            case INACTIVE_MEMBERSHIP -> "El odontólogo está inactivo en esta clínica.";
            case REVOKED -> "La clínica retiró la capacidad clínica de este odontólogo.";
            case NO_CREDENTIAL -> "El odontólogo no declaró su credencial profesional.";
            case EXPIRED -> "La credencial del odontólogo vence el " + DATE.format(licensedUntil)
                + ": no está vigente en la fecha de la cita.";
            case ENABLED -> throw new IllegalArgumentException("ENABLED no es un motivo de rechazo.");
        };
    }

    public ClinicalCapacityState getReason() {
        return reason;
    }

    public LocalDate getExpiredOn() {
        return expiredOn;
    }
}
