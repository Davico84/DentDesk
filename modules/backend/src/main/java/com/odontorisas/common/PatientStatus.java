package com.odontorisas.common;

/**
 * Estado de una ficha de paciente. La baja es <b>lógica</b> (design.md D4): "dar de baja"
 * archiva, nunca borra —la historia clínica tiene retención legal—, y se puede revertir
 * reactivando ({@link #INACTIVE} → {@link #ACTIVE}).
 */
public enum PatientStatus {
    ACTIVE,
    INACTIVE
}
