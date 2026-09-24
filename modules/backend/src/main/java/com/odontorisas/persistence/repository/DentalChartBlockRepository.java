package com.odontorisas.persistence.repository;

import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.persistence.entity.DentalChartBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a {@link DentalChartBlock}. Restringido a la organización del contexto automáticamente
 * (RLS + {@code @TenantId}), mismo patrón que {@code HistoriaClinicaRepository}.
 */
public interface DentalChartBlockRepository extends JpaRepository<DentalChartBlock, Long> {

    /** El bloque abierto del paciente, si existe (unicidad garantizada además por el índice único
     *  parcial {@code ux_dental_chart_block_open}). */
    Optional<DentalChartBlock> findByPatientIdAndStatus(Long patientId, DentalChartBlockStatus status);
}
