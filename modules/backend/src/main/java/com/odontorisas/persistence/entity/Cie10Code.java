package com.odontorisas.persistence.entity;

import com.odontorisas.common.Cie10Level;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un código del catálogo CIE10-MINSA, en cualquiera de sus 4 niveles (design.md D6). Catálogo de
 * <b>referencia global</b>: a propósito NO lleva {@code organization_id} ni {@code @TenantId} — es
 * el mismo catálogo para todas las clínicas, no un dato de negocio de una en particular.
 */
@Entity
@Table(name = "cie10_code")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cie10Code {

    @Id
    @Column(length = 20)
    private String code;

    @Column(length = 500, nullable = false)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Cie10Level nivel;

    /** Código del padre en la jerarquía; {@code null} solo para los capítulos (raíz). */
    @Column(name = "parent_code", length = 20)
    private String parentCode;
}
