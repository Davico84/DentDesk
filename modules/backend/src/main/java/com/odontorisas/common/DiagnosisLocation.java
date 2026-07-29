package com.odontorisas.common;

/**
 * Ubicación de un diagnóstico CIE10 (design.md D5, `add-clinical-history`). {@code PIEZA} exige
 * número FDI; {@code GENERAL} lo rechaza. {@code REGION} se difiere: no hay todavía una
 * clasificación clínica aprobada (cuadrante/maxilar/sector) — no se deja una ubicación aceptada
 * por el contrato sin reglas de validación (ADR-0011).
 */
public enum DiagnosisLocation {
    GENERAL,
    PIEZA
}
