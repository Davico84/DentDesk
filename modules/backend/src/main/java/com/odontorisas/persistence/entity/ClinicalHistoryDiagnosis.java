package com.odontorisas.persistence.entity;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.TipoDiagnostico;
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

/**
 * Un diagnóstico de la Historia Clínica (design.md D5/D11, ADR-0011, norma MINSA HIS). Entidad de
 * negocio bajo RLS <b>propia</b> (design.md D2b) — no hereda el aislamiento de
 * {@link HistoriaClinica} por la FK; {@code organization_id}/{@code @TenantId} van también aquí.
 *
 * <p>Solo se agrega/modifica/elimina mientras la Historia esté en {@code DRAFT} (design.md D5) —
 * lo valida el servicio, no esta entidad.
 */
@Entity
@Table(name = "clinical_history_diagnosis")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicalHistoryDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "historia_id", nullable = false, updatable = false)
    private Long historiaId;

    /** Código CIE10 (validado contra {@code cie10_code} en el servicio, no aquí). */
    @Column(name = "cie10_code", nullable = false, length = 20)
    private String cie10Code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiagnosisLocation ubicacion;

    /** Número FDI; obligatorio solo si {@link #ubicacion} es {@code PIEZA} (design.md D5). */
    @Column(name = "fdi_number")
    private Integer fdiNumber;

    /** Norma MINSA HIS (design.md D11): un diagnóstico no es una cadena suelta. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_diagnostico", nullable = false, length = 20)
    private TipoDiagnostico tipoDiagnostico;

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
