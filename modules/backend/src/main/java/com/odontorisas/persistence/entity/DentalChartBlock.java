package com.odontorisas.persistence.entity;

import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.common.DentalChartBlockType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

/**
 * El odontograma como <b>documento fechado</b> (design.md D1/D3/D9, ADR-0012, ADR-0017). Un bloque
 * = un odontograma sellado en un momento del expediente; NTS 188 lo genera <b>por evento
 * clínico</b> (5.10/5.11), no uno por día (ADR-0017 D3). En v1 solo existe {@link
 * DentalChartBlockType#INITIAL}.
 *
 * <p>Entidad de negocio bajo RLS <b>propia</b> — mismo patrón que {@link HistoriaClinica}: colgar
 * de {@code patientId} no exime del aislamiento; RLS es por tabla, no se hereda por FK.
 *
 * <p>Ciclo de vida unidireccional {@code OPEN → SEALED}, coordinado atómicamente con el sellado de
 * {@link HistoriaClinica} (ADR-0012 D5) — el servicio, no esta entidad, aplica esa coordinación y
 * rechaza cualquier transición inversa (design.md D4/D9).
 */
@Entity
@Table(name = "dental_chart_block")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DentalChartBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DentalChartBlockType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DentalChartBlockStatus status;

    /** Fecha del evento clínico que generó el bloque (NTS 188 5.10/5.11), no la de creación de la fila. */
    @Column(name = "examined_on", nullable = false)
    private LocalDate examinedOn;

    @Column(name = "sealed_at")
    private Instant sealedAt;

    // --- Dos campos de texto DISTINTOS (ADR-0017 D6) ---
    // "revisado y sin nada que anotar" es un dato clínico válido, distinto de "nunca revisado"
    // (design.md D5) — mismo patrón que alergias_revisado en HistoriaClinica.
    @Column(columnDefinition = "text")
    private String specifications;

    @Column(name = "specifications_reviewed", nullable = false)
    private boolean specificationsReviewed;

    @Column(columnDefinition = "text")
    private String observations;

    @Column(name = "observations_reviewed", nullable = false)
    private boolean observationsReviewed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;
}
