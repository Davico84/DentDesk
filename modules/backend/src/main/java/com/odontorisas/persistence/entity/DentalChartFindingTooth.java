package com.odontorisas.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
 * Una pieza de un hallazgo de alcance {@code TOOTH_RANGE} (prótesis parcial fija, ortodoncia
 * fija) — conjunto ordenado de cardinalidad variable, no cabe en columnas planas del padre
 * (design.md D1/D2).
 *
 * <p>{@link #position} SÍ es dato (identifica los pilares, ADR-0017 D4): a diferencia del orden de
 * {@code BETWEEN_TEETH}, que se normaliza, acá se conserva tal como se registró.
 *
 * <p>Entidad de negocio bajo RLS <b>propia</b> — la de {@link DentalChartFinding} no la protege
 * (la RLS de Postgres no se hereda por FK; mismo hallazgo pagado en {@code add-clinical-history} y
 * repetido en cada tabla nueva de este change).
 *
 * <p>Aunque estas filas nacen y mueren con su {@link DentalChartFinding} (sin edición
 * independiente), llevan autoría completa igual: el candado ArchUnit {@code
 * business_entities_with_tenant_id_must_declare_authorship} es incondicional para toda entidad con
 * {@code @TenantId}, sin excepción por "el padre ya la cubre".
 */
@Entity
@Table(name = "dental_chart_finding_tooth")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DentalChartFindingTooth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "finding_id", nullable = false, updatable = false)
    private Long findingId;

    @Column(nullable = false, updatable = false)
    private Integer position;

    @Column(nullable = false, updatable = false)
    private Integer tooth;

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
