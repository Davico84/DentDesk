package com.odontorisas.common;

import java.time.LocalDate;

/**
 * Foto de la credencial de quien firma un acto clínico, tomada en el momento de firmar (ADR-0016 D1,
 * ADR-0018 D6): se copia al registro y un cambio posterior del firmante no la altera. La produce
 * {@code ClinicalCapacityService.require()}; la consumen las firmas de la admisión y de la nota.
 */
public record SignerSnapshot(Long userId, String fullName, String copNumber, LocalDate licensedUntil) {}
