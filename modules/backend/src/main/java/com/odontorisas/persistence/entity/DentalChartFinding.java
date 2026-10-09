package com.odontorisas.persistence.entity;

import com.odontorisas.common.FindingCondition;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

/**
 * Un hallazgo clínico observado en el odontograma: el catálogo cerrado de 38 tipos de NTS 188
 * (ADR-0017 D5/D7, {@link com.odontorisas.common.FindingType}), localizado según una de cinco
 * formas (design.md D2).
 *
 * <p>Entidad de negocio bajo RLS <b>propia</b> — cuelga de {@link DentalChartBlock} por FK
 * compuesta por tenant (design.md D1), pero la RLS no se hereda: {@code organization_id} y
 * {@code @TenantId} van también acá.
 *
 * <p>{@link #condition} es el único dato de color que se persiste (ADR-0017 D1): obligatorio solo
 * cuando el {@code colorBehavior} del tipo es {@code SEGUN_ESTADO} — esa invariante (INV-11) vive
 * en el boundary, no en esta entidad, porque depende del catálogo (design.md D9).
 * {@link #grade} solo lo exige {@code MOVILIDAD_PATOLOGICA} (6.1.19).
 */
@Entity
@Table(name = "dental_chart_finding")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DentalChartFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "block_id", nullable = false, updatable = false)
    private Long blockId;

    /** Clave del catálogo {@code dental_finding_catalog} (V16), junto con {@link #subtype}. */
    @Column(nullable = false, length = 48, updatable = false)
    private String type;

    /** {@code ""} cuando el tipo no tiene subtipos — nunca {@code null} (rompería la FK compuesta
     *  a la clave del catálogo, que también usa {@code ""}). */
    @Column(nullable = false, length = 8, updatable = false)
    @Builder.Default
    private String subtype = "";

    @Enumerated(EnumType.STRING)
    private FindingCondition condition;

    private Short grade;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "locationKind", column = @Column(name = "location_kind")),
        @AttributeOverride(name = "toothA", column = @Column(name = "tooth_a")),
        @AttributeOverride(name = "toothB", column = @Column(name = "tooth_b")),
        @AttributeOverride(name = "surface", column = @Column(name = "surface")),
        @AttributeOverride(name = "arch", column = @Column(name = "arch"))
    })
    private DentalChartLocation location;

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
