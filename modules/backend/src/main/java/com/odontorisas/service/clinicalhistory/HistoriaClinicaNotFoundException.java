package com.odontorisas.service.clinicalhistory;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * No hay Historia Clínica para ese paciente en la clínica activa (404 Not Found). Igual que
 * {@code PatientNotFoundException}: también se responde 404 si el paciente/historia pertenece a
 * otra clínica — el aislamiento la hace invisible, sin revelar que existe en otra parte.
 */
public class HistoriaClinicaNotFoundException extends BusinessException {
    public HistoriaClinicaNotFoundException() {
        super("No se ha encontrado la Historia Clínica.", HttpStatus.NOT_FOUND, "clinical-history-not-found");
    }
}
