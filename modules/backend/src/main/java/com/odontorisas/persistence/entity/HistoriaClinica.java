package com.odontorisas.persistence.entity;

import com.odontorisas.common.HistoriaClinicaStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Map;

/**
 * Historia Clínica: documento médico-legal inicial de la atención, una por paciente por clínica
 * de por vida (ADR-0011). Entidad de negocio bajo RLS, <b>mismo patrón que {@link Patient}</b>
 * (design.md D2b, `add-clinical-history`) — el hecho de colgar de {@code patientId} no la exime
 * del aislamiento propio: RLS es por tabla, no se hereda por FK.
 *
 * <p>Ciclo de vida unidireccional {@code DRAFT → COMPLETED} (design.md D1). En {@code DRAFT} es
 * editable libremente; en {@code COMPLETED} la información inicial queda sellada — el servicio,
 * no esta entidad, es quien rechaza la escritura (docs/backend.md: la validación de negocio vive
 * en el servicio).
 */
@Entity
@Table(name = "historia_clinica")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoriaClinica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Clínica dueña de la historia. La rellena y filtra Hibernate desde el contexto de tenant. */
    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    /** Paciente al que pertenece; único por clínica (una Historia Clínica de por vida, ADR-0011). */
    @Column(name = "patient_id", nullable = false, updatable = false)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HistoriaClinicaStatus status;

    // --- Documento inicial (design.md D1-D5) ---
    @Column(name = "motivo_consulta", columnDefinition = "text")
    private String motivoConsulta;

    @Column(name = "enfermedad_actual", columnDefinition = "text")
    private String enfermedadActual;

    @Column(name = "antecedentes_personales", columnDefinition = "text")
    private String antecedentesPersonales;

    @Column(name = "examen_clinico", columnDefinition = "text")
    private String examenClinico;

    @Column(columnDefinition = "text")
    private String pronostico;

    /** Estructura libre (PA, FC, temperatura...) — sin queries por dentro (design.md Open Question). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "signos_vitales", columnDefinition = "jsonb")
    private Map<String, Object> signosVitales;

    // --- "Campo vacío ≠ revisado" (design.md D3) ---
    @Column(name = "alergias_revisado", nullable = false)
    private boolean alergiasRevisado;

    @Column(name = "alergias_detalle", columnDefinition = "text")
    private String alergiasDetalle;

    @Column(name = "medicacion_revisado", nullable = false)
    private boolean medicacionRevisado;

    @Column(name = "medicacion_detalle", columnDefinition = "text")
    private String medicacionDetalle;

    @Column(name = "antecedentes_familiares_revisado", nullable = false)
    private boolean antecedentesFamiliaresRevisado;

    @Column(name = "antecedentes_familiares_detalle", columnDefinition = "text")
    private String antecedentesFamiliaresDetalle;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Quién cerró la admisión, y su credencial en ese momento (update-staff-professional-credential D5). */
    @Column(name = "completed_by")
    private Long completedBy;

    @Embedded
    private Signature signature;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Autoría automática (docs/backend.md §8.6) — la rellena {@code IdentityAuditorAware}. */
    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;
}
