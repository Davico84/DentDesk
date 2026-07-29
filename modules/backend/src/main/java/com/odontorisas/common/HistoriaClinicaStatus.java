package com.odontorisas.common;

/**
 * Ciclo de vida de la Historia Clínica (design.md D1, `add-clinical-history`). Transición
 * unidireccional: solo {@code DRAFT} → {@code COMPLETED}, manual. Sin reapertura en esta
 * capacidad (llega en fase 2, `update-clinical-history`, junto con {@code AMENDED}).
 */
public enum HistoriaClinicaStatus {
    DRAFT,
    COMPLETED
}
