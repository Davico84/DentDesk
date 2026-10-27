package com.odontorisas.persistence.entity;

import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Puente identidad↔organización: define a qué organización pertenece un usuario y con
 * qué rol. Es la entidad que carga la autorización (el rol vive aquí, no en {@link User}).
 * Aislada por IDENTIDAD mediante Row-Level Security (policy sobre {@code user_id}).
 */
@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    /**
     * Retiro de la capacidad clínica en esta clínica por su administrador ({@code
     * update-staff-professional-credential} design.md D2). Nulo si no hay retiro; los dos van juntos
     * (CHECK de V30) y solo los escribe un ADMIN activo sobre un MEMBER ajeno (trigger de V30).
     */
    @Column(name = "clinical_capacity_revoked_at")
    private Instant clinicalCapacityRevokedAt;

    @Column(name = "clinical_capacity_revoked_by")
    private Long clinicalCapacityRevokedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
