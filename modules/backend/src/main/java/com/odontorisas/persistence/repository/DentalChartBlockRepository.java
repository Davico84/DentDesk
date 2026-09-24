package com.odontorisas.persistence.repository;

import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.persistence.entity.DentalChartBlock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Acceso a {@link DentalChartBlock}. Restringido a la organización del contexto automáticamente
 * (RLS + {@code @TenantId}), mismo patrón que {@code HistoriaClinicaRepository}.
 */
public interface DentalChartBlockRepository extends JpaRepository<DentalChartBlock, Long> {

    /** El bloque abierto del paciente, si existe (unicidad garantizada además por el índice único
     *  parcial {@code ux_dental_chart_block_open}). */
    Optional<DentalChartBlock> findByPatientIdAndStatus(Long patientId, DentalChartBlockStatus status);

    /** {@code SELECT ... FOR UPDATE}: serializa la creación de hallazgos de rango contra el mismo
     *  bloque, para validar su cardinalidad y su unicidad con el hallazgo bloqueado — la única
     *  variante de localización cuya unicidad no garantiza un índice (design.md D2/D3). Método
     *  aparte de {@code findById} a propósito: bloquear en cada lectura simple degradaría la
     *  concurrencia de todo lo demás que usa este repositorio. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from DentalChartBlock b where b.id = :id")
    Optional<DentalChartBlock> lockById(@Param("id") Long id);
}
