package com.odontorisas.service.dentalchart;

import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.common.DentalChartBlockType;
import com.odontorisas.persistence.entity.DentalChartBlock;

import java.time.Instant;
import java.time.LocalDate;

public record DentalChartBlockView(
    Long id,
    Long patientId,
    DentalChartBlockType type,
    DentalChartBlockStatus status,
    LocalDate examinedOn,
    Instant sealedAt,
    String specifications,
    boolean specificationsReviewed,
    String observations,
    boolean observationsReviewed) {

    public static DentalChartBlockView from(DentalChartBlock b) {
        return new DentalChartBlockView(
            b.getId(), b.getPatientId(), b.getType(), b.getStatus(), b.getExaminedOn(), b.getSealedAt(),
            b.getSpecifications(), b.isSpecificationsReviewed(),
            b.getObservations(), b.isObservationsReviewed());
    }
}
