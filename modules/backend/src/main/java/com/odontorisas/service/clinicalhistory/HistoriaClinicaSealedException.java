package com.odontorisas.service.clinicalhistory;

import com.odontorisas.service.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * La Historia Clínica ya está {@code COMPLETED}: su información inicial quedó sellada y no se
 * edita directamente (design.md D1, spec: sellada tras COMPLETED). El rechazo es <b>server-side
 * siempre</b>, aunque la petición llegue directa a la API sin pasar por la interfaz.
 */
public class HistoriaClinicaSealedException extends BusinessException {
    public HistoriaClinicaSealedException() {
        super("La Historia Clínica ya está sellada; su información inicial no se puede editar.",
            HttpStatus.CONFLICT, "clinical-history-sealed");
    }
}
