package com.odontorisas.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Autorizacion global. No es una membresia y no pertenece a una organizacion. */
@Entity
@Table(name = "platform_admins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformAdmin {
    /** Derivado de {@link #user} ({@code @MapsId}): no se asigna a mano. */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /*
     * @MapsId y no un @Id asignado + @JoinColumn(insertable = false): con el id ya puesto, Spring
     * Data trata la entidad como existente y hace merge, y Hibernate falla con "null identifier".
     */
    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
