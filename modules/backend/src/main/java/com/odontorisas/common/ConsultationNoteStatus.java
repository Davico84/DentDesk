package com.odontorisas.common;

/**
 * Ciclo de vida de la nota de consulta (`update-clinical-history` design.md D2, ADR-0020 D2).
 * Unidireccional: {@code ABIERTA} (borrador editable) → {@code SELLADA} (inmutable). Sin cierre
 * diario: esa regla es de hospitalización (NTS 139 §5.2.1 (3)), no de consulta externa.
 */
public enum ConsultationNoteStatus {
    ABIERTA,
    SELLADA
}
