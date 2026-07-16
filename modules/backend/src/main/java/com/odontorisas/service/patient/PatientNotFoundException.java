package com.odontorisas.service.patient;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * No hay un paciente con ese id en la clínica activa (404 Not Found). Se responde <b>404</b>
 * —no 403— también cuando el paciente existe en <b>otra</b> clínica: el aislamiento (RLS +
 * {@code @TenantId}) hace que sea invisible, y no se revela que existe en otra parte.
 */
public class PatientNotFoundException extends BusinessException {
    public PatientNotFoundException() {
        super("No se ha encontrado el paciente.", HttpStatus.NOT_FOUND, "patient-not-found");
    }
}
