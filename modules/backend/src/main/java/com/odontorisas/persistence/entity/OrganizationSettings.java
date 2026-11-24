package com.odontorisas.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

/**
 * Configuración 1:1 de una organización (punto de extensión: zona horaria, locale,
 * moneda). Comparte la clave primaria con {@link Organization} vía {@code @MapsId}.
 */
@Entity
@Table(name = "organization_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationSettings {

    /** Valores por defecto (coinciden con los de la migración V6). */
    public static final String DEFAULT_TIMEZONE = "America/Lima";
    public static final String DEFAULT_LOCALE = "es-PE";
    public static final String DEFAULT_CURRENCY = "PEN";

    @Id
    @Column(name = "organization_id")
    private Long organizationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false, length = 10)
    private String locale;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Horario de atención (add-appointments design.md D5): tramos por día en hora de la clínica,
     * {@code "HH:mm"}. {@code insertable = false}: al crear la organización lo pone el {@code DEFAULT} de la
     * columna (V32), así el alta de plataforma no lo conoce.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "business_hours", columnDefinition = "jsonb", nullable = false, insertable = false)
    private Map<DayOfWeek, List<StoredSegment>> businessHours;

    /** Forma persistida de un tramo: horas como texto {@code "HH:mm"}. */
    public record StoredSegment(String opens, String closes) {
    }

    /** Crea la configuración por defecto para una organización recién creada. */
    public static OrganizationSettings defaultsFor(Organization organization) {
        return OrganizationSettings.builder()
                .organization(organization)
                .timezone(DEFAULT_TIMEZONE)
                .locale(DEFAULT_LOCALE)
                .currency(DEFAULT_CURRENCY)
                .build();
    }
}
