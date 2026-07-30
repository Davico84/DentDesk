package com.odontorisas.service.clinicalhistory;

/**
 * Estado de habilitación de un paciente para tratamientos (design.md D8). {@code status} es
 * {@code NONE|DRAFT|COMPLETED}; {@code enabledForTreatment} es {@code true} solo si
 * {@code COMPLETED} — es la señal que consumirán `treatments`/`treatment-plans` cuando existan.
 */
public record EnablementView(String status, boolean enabledForTreatment) {
}
