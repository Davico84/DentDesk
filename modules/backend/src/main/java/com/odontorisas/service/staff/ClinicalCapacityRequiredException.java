package com.odontorisas.service.staff;

import com.odontorisas.common.ClinicalCapacityState;
import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Firmar exige capacidad clínica en la clínica activa (403). Lleva el motivo ({@code reason}) y, si la
 * habilitación venció, la fecha ({@code expiredOn}), para que el cliente explique por qué no se puede
 * firmar ({@code update-staff-professional-credential} design.md D3).
 */
public class ClinicalCapacityRequiredException extends BusinessException {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ClinicalCapacityState reason;
    private final LocalDate expiredOn;

    public ClinicalCapacityRequiredException(ClinicalCapacityState reason, LocalDate licensedUntil) {
        super(message(reason, licensedUntil), HttpStatus.FORBIDDEN, "clinical-capacity-required");
        this.reason = reason;
        this.expiredOn = reason == ClinicalCapacityState.EXPIRED ? licensedUntil : null;
    }

    private static String message(ClinicalCapacityState reason, LocalDate licensedUntil) {
        return switch (reason) {
            case INACTIVE_MEMBERSHIP -> "Tu cuenta está inactiva en esta clínica: no puedes firmar actos clínicos.";
            case REVOKED -> "La clínica retiró tu capacidad clínica: no puedes firmar actos clínicos.";
            case NO_CREDENTIAL -> "Para firmar actos clínicos, declara tu credencial profesional.";
            case EXPIRED -> "Tu habilitación venció el " + DATE.format(licensedUntil)
                + ": renueva tu credencial profesional para firmar.";
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
