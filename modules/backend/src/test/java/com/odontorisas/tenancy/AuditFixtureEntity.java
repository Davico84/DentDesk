package com.odontorisas.tenancy;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Entidad de prueba <b>real</b> (no consulta nativa) para verificar el ciclo de vida JPA de
 * Spring Data Auditing. A diferencia de {@link TestBusinessAccess} —que usa SQL nativo a
 * propósito, para aislar la prueba de RLS de cualquier ayuda de la capa de aplicación—
 * {@code @CreatedBy}/{@code @LastModifiedBy} <b>solo</b> se disparan por el ciclo de vida de una
 * {@code @Entity} gestionada (persist/merge), así que hace falta una entidad de verdad.
 *
 * <p>Su tabla no está en Flyway (es fixture, no esquema real): la crea el propio test.
 */
@Entity
@Table(name = "test_audit_fixture")
@EntityListeners(AuditingEntityListener.class)
class AuditFixtureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    @CreatedBy
    private Long createdBy;

    @LastModifiedBy
    private Long updatedBy;

    protected AuditFixtureEntity() {
    }

    AuditFixtureEntity(String label) {
        this.label = label;
    }

    Long getId() {
        return id;
    }

    String getLabel() {
        return label;
    }

    void setLabel(String label) {
        this.label = label;
    }

    Long getCreatedBy() {
        return createdBy;
    }

    Long getUpdatedBy() {
        return updatedBy;
    }
}
