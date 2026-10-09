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

    /** ¿Tiene el paciente un odontograma inicial? Sin cargar la entidad: así no queda en el
     *  contexto de persistencia una copia que un {@link #lockInitialByPatientId} posterior en la
     *  misma transacción devolvería sin refrescar. */
    boolean existsByPatientId(Long patientId);

    /**
     * {@code SELECT ... FOR UPDATE} del odontograma inicial del paciente (único por el índice
     * {@code ux_dental_chart_block_initial}). Toda escritura del bloque o de sus hallazgos pasa por
     * acá antes de comprobar que siga {@code OPEN} (design.md D9): sin el bloqueo, una escritura que
     * leyó {@code OPEN} se cuela mientras otra transacción sella — y como Hibernate actualiza todas
     * las columnas, guardar el bloque con ese estado viejo lo reabriría. Tiene que ser la primera
     * carga del bloque en la transacción: si ya estuviera en el contexto de persistencia, Hibernate
     * devolvería esa instancia sin refrescar su estado.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from DentalChartBlock b where b.patientId = :patientId "
        + "and b.type = com.odontorisas.common.DentalChartBlockType.INITIAL")
    Optional<DentalChartBlock> lockInitialByPatientId(@Param("patientId") Long patientId);
}
