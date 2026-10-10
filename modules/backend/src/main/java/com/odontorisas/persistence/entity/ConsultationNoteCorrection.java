package com.odontorisas.persistence.entity;

import com.odontorisas.common.NoteTextField;
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
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Corrección de un error material de una nota sellada (design.md D6, DS 009-2017-SA art. 47 c,
 * NTS 139 §5.3.3 j): un registro <b>nuevo</b> con el campo, el texto corregido y el motivo. Nunca
 * se modifica ni se borra — {@link Immutable} en Hibernate y trigger en V22. RLS propia.
 */
@Entity
@Immutable
@Table(name = "consultation_note_correction")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationNoteCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "note_id", nullable = false, updatable = false)
    private Long noteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private NoteTextField field;

    @Column(name = "corrected_text", nullable = false, updatable = false, columnDefinition = "text")
    private String correctedText;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Siempre igual a {@link #createdAt}: la fila no se actualiza nunca (autoría completa, §8.6). */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, updatable = false)
    private Long updatedBy;
}
