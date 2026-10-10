package com.odontorisas.common;

/**
 * Asiento de antecedente (design.md D7): un {@code ALTA} registra un antecedente; una {@code BAJA}
 * cierra un alta vigente sin borrarlo. Lo vigente se calcula, no se guarda.
 */
public enum BackgroundEntryKind {
    ALTA,
    BAJA
}
