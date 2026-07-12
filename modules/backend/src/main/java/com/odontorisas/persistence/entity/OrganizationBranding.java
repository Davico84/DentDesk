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

/**
 * Marca visual 1:1 de una organización (colores de marca y logo). Comparte la clave
 * primaria con {@link Organization} vía {@code @MapsId}. Todos los campos son nullable:
 * ausencia de fila (o de campo) = valores por defecto del producto. Es metadato del
 * tenant (sin Row-Level Security, como {@link OrganizationSettings}).
 */
@Entity
@Table(name = "organization_branding")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationBranding {

    @Id
    @Column(name = "organization_id")
    private Long organizationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "organization_id")
    private Organization organization;

    /** Color primario de marca, hex {@code #RRGGBB} (o {@code null} = default del producto). */
    @Column(name = "brand_primary", length = 9)
    private String brandPrimary;

    /** Color secundario, hex {@code #RRGGBB} (o {@code null} = derivado del primario). */
    @Column(name = "brand_secondary", length = 9)
    private String brandSecondary;

    /** URL {@code https} del logo (o {@code null} = logo del producto). */
    @Column(name = "logo_url", length = 2048)
    private String logoUrl;
}
