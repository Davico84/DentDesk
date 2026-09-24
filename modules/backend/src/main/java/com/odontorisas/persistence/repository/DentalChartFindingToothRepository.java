package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.DentalChartFindingTooth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a {@link DentalChartFindingTooth}. Restringido a la organización del contexto
 * automáticamente (RLS + {@code @TenantId}) — <b>propia</b>, no heredada de {@code
 * dental_chart_finding} por la FK.
 */
public interface DentalChartFindingToothRepository extends JpaRepository<DentalChartFindingTooth, Long> {

    /** "¿Qué hallazgos tocan la pieza N?" — la razón de ser de esta tabla (design.md D2). */
    List<DentalChartFindingTooth> findByTooth(Integer tooth);

    List<DentalChartFindingTooth> findByFindingIdOrderByPosition(Long findingId);
}
