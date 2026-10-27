package com.odontorisas.presentation.dto;

import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.common.DentalChartBlockType;
import com.odontorisas.service.dentalchart.DentalChartBlockView;
import com.odontorisas.service.dentalchart.DentalChartFindingView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DentalChartResponse(
    Long id,
    Long patientId,

    @Schema(description = "Hoy siempre INITIAL: la evolución con varios bloques por paciente es fase 2.")
    DentalChartBlockType type,

    @Schema(description = "OPEN | SEALED. SEALED es de solo lectura, incluso por API directa.")
    DentalChartBlockStatus status,

    @Schema(description = "Fecha del evento clínico (NTS 188 5.10/5.11), no la de creación de la fila.")
    LocalDate examinedOn,

    @Schema(description = "Momento del sellado. Ausente mientras el bloque siga OPEN.", nullable = true)
    Instant sealedAt,

    @Schema(description = "Texto libre. NULL válido si specificationsReviewed=true: 'revisado, nada que anotar'.",
        nullable = true)
    String specifications,

    @Schema(description = "true si un profesional revisó esta sección, con o sin contenido.")
    boolean specificationsReviewed,

    @Schema(description = "Texto libre. NULL válido si observationsReviewed=true.", nullable = true)
    String observations,

    boolean observationsReviewed,

    List<DentalChartFindingResponse> findings,

    @Schema(description = "Credencial de quien lo selló al cerrar la admisión; null si está OPEN o se firmó "
        + "antes del registro de credenciales.", nullable = true)
    SignerResponse signer,

    @Schema(description = "true si está SEALED pero se firmó antes del registro de credenciales (sin firmante).")
    boolean signedBeforeCredentials) {

    public static DentalChartResponse from(DentalChartBlockView b, List<DentalChartFindingView> findings) {
        return new DentalChartResponse(
            b.id(), b.patientId(), b.type(), b.status(), b.examinedOn(), b.sealedAt(),
            b.specifications(), b.specificationsReviewed(), b.observations(), b.observationsReviewed(),
            findings.stream().map(DentalChartFindingResponse::from).toList(),
            SignerResponse.from(b.signer()), b.signedBeforeCredentials());
    }
}
