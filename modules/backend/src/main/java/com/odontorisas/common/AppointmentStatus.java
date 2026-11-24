package com.odontorisas.common;

/**
 * Estado de una cita (add-appointments). {@code AGENDADA} es el único no final: pasa a {@code ATENDIDA},
 * {@code NO_ASISTIO} o {@code CANCELADA}, y de ahí no cambia más. {@code AGENDADA} y {@code ATENDIDA} ocupan
 * el hueco del odontólogo; las otras dos lo liberan (design.md D2).
 */
public enum AppointmentStatus {
    AGENDADA,
    ATENDIDA,
    NO_ASISTIO,
    CANCELADA;

    public boolean isFinal() {
        return this != AGENDADA;
    }
}
