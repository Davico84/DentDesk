package com.odontorisas.persistence.entity;

import com.odontorisas.common.ConsultationNoteStatus;
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
 * La nota de consulta: una por atención posterior a la admisión (`update-clinical-history`
 * design.md D1–D4, ADR-0020). Agregado propio colgado del paciente — no fila hija de
 * {@link HistoriaClinica}: tiene ciclo de vida propio y se crea muchas veces. La Historia
 * {@code COMPLETED} es una <b>precondición</b> que verifica el servicio, no una FK de composición.
 *
 * <p>RLS <b>propia</b>, como {@link DentalChartBlock}. Ciclo {@code ABIERTA → SELLADA}; la
 * inmutabilidad la aplican el servicio (bloquear y después comprobar) y los triggers de V22.
 */
@Entity
@Table(name = "consultation_note")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationNote {

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
    private ConsultationNoteStatus status;

    /** Cuándo ocurrió la atención, ingresado por el odontólogo (design.md D4) — no {@link #createdAt}. */
    @Column(name = "attended_at", nullable = false)
    private Instant attendedAt;

    // --- Contenido de NTS 139 §5.2.1 (1), unión de 1.1 d y 1.2 (design.md D3) ---
    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "illness_duration", columnDefinition = "text")
    private String illnessDuration;

    @Column(name = "symptoms_and_signs", columnDefinition = "text")
    private String symptomsAndSigns;

    @Column(name = "previous_treatment", columnDefinition = "text")
    private String previousTreatment;

    @Column(name = "biological_functions", columnDefinition = "text")
    private String biologicalFunctions;

    @Column(columnDefinition = "text")
    private String examination;

    @Column(columnDefinition = "text")
    private String treatment;

    @Column(name = "auxiliary_exams", columnDefinition = "text")
    private String auxiliaryExams;

    @Column(columnDefinition = "text")
    private String referral;

    /** Solo fecha (design.md D3): {@code appointments} decidirá si la consume. */
    @Column(name = "next_appointment_on")
    private LocalDate nextAppointmentOn;

    @Column(name = "sealed_at")
    private Instant sealedAt;

    /** Quien selló = quien firma; el único que puede corregir la nota (design.md D6). */
    @Column(name = "sealed_by")
    private Long sealedBy;

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
