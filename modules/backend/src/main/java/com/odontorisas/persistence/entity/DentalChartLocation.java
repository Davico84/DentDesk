package com.odontorisas.persistence.entity;

import com.odontorisas.common.DentalArch;
import com.odontorisas.common.LocationKind;
import com.odontorisas.common.ToothSurface;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Localización polimórfica de un hallazgo del odontograma: discriminador explícito + columnas
 * nullable (design.md D2, ADR-0017 D4) — cinco variantes, no las tres de {@code ADR-0012} D8.
 *
 * <p>La coherencia entre {@link #locationKind} y las columnas pobladas la garantiza el {@code
 * CHECK dental_chart_finding_location_paired} de la base de datos (V18), <b>a prueba de
 * {@code NULL}</b>: cada rama del {@code CASE} usa {@code IS NULL}/{@code IS NOT NULL} explícitos,
 * porque en PostgreSQL un {@code CHECK} que evalúa a {@code NULL} se considera satisfecho. Esta
 * clase no repite esa validación — es responsabilidad del boundary (el futuro {@code
 * @ValidFindingLocation}, design.md D9), no de la entidad.
 *
 * <p>{@link #toothA} es la pieza principal en {@code TOOTH} y la menor en {@code BETWEEN_TEETH}
 * (orden canónico: se persiste siempre {@code toothA < toothB}, normalizado antes de llegar acá).
 * {@code TOOTH_RANGE} no puebla ninguna columna propia: su conjunto de piezas vive en la tabla
 * hija {@link DentalChartFindingTooth}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DentalChartLocation {

    @Enumerated(EnumType.STRING)
    @Column(name = "location_kind", nullable = false, length = 16)
    private LocationKind locationKind;

    @Column(name = "tooth_a")
    private Integer toothA;

    @Column(name = "tooth_b")
    private Integer toothB;

    // Convertido a código de una letra (M/D/V/P/L/O/I) por ToothSurfaceConverter, autoApply=true
    // — no @Enumerated: el CHECK de la columna espera la letra, no el nombre del enum de Java.
    @Column(length = 4)
    private ToothSurface surface;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private DentalArch arch;
}
