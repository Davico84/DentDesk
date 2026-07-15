package com.odontorisas.persistence.entity;

import com.odontorisas.common.Ages;
import com.odontorisas.common.PatientStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Ficha clínico-administrativa de la persona atendida en una clínica. <b>Primera entidad de
 * negocio aislada por organización</b> (docs/backend.md §8.1): lleva {@code organization_id} con
 * {@link TenantId} —Hibernate la rellena y filtra sola— y su tabla tiene Row-Level Security.
 *
 * <p><b>No hay código de aislamiento aquí:</b> el contexto de organización lo fija
 * {@code TenantAwareTransactionManager} en cada transacción. Declarar la entidad según la
 * convención basta — por defecto, no por disciplina.
 *
 * <p>El paciente <b>no es un usuario</b> del sistema: no tiene cuenta ni se vincula a un
 * {@code User}. Es un cliente de la clínica (docs/domain.md).
 */
@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Organización (clínica) dueña de la ficha. La rellena y filtra Hibernate desde el contexto
     * de tenant; NUNCA se toma de la petición. {@code updatable = false}: un paciente no cambia
     * de clínica (la misma persona en otra clínica es OTRA ficha — ADR-0004).
     */
    @TenantId
    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    // --- Datos personales (fullName y birthDate son los únicos obligatorios) ---
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    /** Documento de identidad, opcional; único POR CLÍNICA (ADR-0004). */
    @Column(length = 50)
    private String document;

    /** De la fecha de nacimiento se deriva {@link #isMinor()}; la minoría NO se persiste. */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(length = 20)
    private String sex;

    // --- Contacto ---
    @Column(length = 40)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String address;

    // --- Responsable / tutor (obligatorio para menores; guardianDocument opcional) ---
    @Column(name = "guardian_name", length = 200)
    private String guardianName;

    @Column(name = "guardian_relationship", length = 50)
    private String guardianRelationship;

    @Column(name = "guardian_document", length = 50)
    private String guardianDocument;

    @Column(name = "guardian_phone", length = 40)
    private String guardianPhone;

    // --- Contacto de emergencia ---
    @Column(name = "emergency_contact_name", length = 200)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 40)
    private String emergencyContactPhone;

    @Column(name = "emergency_contact_relationship", length = 50)
    private String emergencyContactRelationship;

    // --- Anamnesis como JSONB en la propia fila (design.md D7) ---
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> allergies;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> conditions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_medication", columnDefinition = "jsonb")
    private List<String> currentMedication;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Minoría de edad <b>derivada</b> de la fecha de nacimiento (design.md D2): se recalcula en
     * cada consulta, así que nunca se desincroniza el día del cumpleaños. Es {@code @Transient}:
     * un dato declarado se desincronizaría; uno derivado, jamás.
     */
    @jakarta.persistence.Transient
    public boolean isMinor() {
        return Ages.isMinor(birthDate);
    }
}
