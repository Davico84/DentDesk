package com.odontorisas.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Credencial profesional que el propio usuario declara: número de colegiatura COP y fecha hasta la que
 * está habilitado ({@code update-staff-professional-credential} design.md D1). Una por usuario; vale en
 * todas sus clínicas. La RLS solo deja escribir la propia.
 *
 * <p>Implementa {@link Persistable}: el id es el {@code user_id} (asignado, no generado), y sin esto
 * Spring Data trataría toda credencial como existente y haría un {@code merge}. Así la primera
 * declaración es un {@code INSERT} y la renovación un {@code UPDATE} de la fila ya cargada, nunca un
 * upsert (design.md D1).
 */
@Entity
@Table(name = "professional_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfessionalCredential implements Persistable<Long> {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Setter
    @Column(name = "cop_number", nullable = false, length = 10)
    private String copNumber;

    @Setter
    @Column(name = "licensed_until", nullable = false)
    private LocalDate licensedUntil;

    @Setter
    @Column(name = "sworn_at", nullable = false)
    private Instant swornAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Transient
    private boolean isNew = true;

    public ProfessionalCredential(Long userId, String copNumber, LocalDate licensedUntil, Instant swornAt) {
        this.userId = userId;
        this.copNumber = copNumber;
        this.licensedUntil = licensedUntil;
        this.swornAt = swornAt;
    }

    @Override
    public Long getId() {
        return userId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
