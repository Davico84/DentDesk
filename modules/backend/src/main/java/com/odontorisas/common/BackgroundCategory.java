package com.odontorisas.common;

/**
 * Categorías de los antecedentes nuevos registrados en la consulta (design.md D7, ADR-0020 D6).
 * {@code ALERGIA_RAM} reúne alergias y sospechas de reacción adversa a medicamentos (NTS 139
 * §4.2.10): para alertar antes de prescribir son la misma señal.
 */
public enum BackgroundCategory {
    ALERGIA_RAM,
    ANTECEDENTE_PATOLOGICO,
    MEDICACION
}
