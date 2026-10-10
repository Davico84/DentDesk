package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.DentalChartFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a {@link DentalChartFinding}. Restringido a la organización del contexto automáticamente
 * (RLS + {@code @TenantId}).
 */
public interface DentalChartFindingRepository extends JpaRepository<DentalChartFinding, Long> {

    List<DentalChartFinding> findByBlockId(Long blockId);
}
