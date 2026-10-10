package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.HistoriaClinica;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Acceso a {@link HistoriaClinica}. Todas las consultas quedan restringidas a la organización del
 * contexto <b>automáticamente</b> ({@code @TenantId} + Row-Level Security) — sin condición de
 * organización escrita a mano, mismo patrón que {@code PatientRepository}.
 */
public interface HistoriaClinicaRepository extends JpaRepository<HistoriaClinica, Long> {

    /** Una por paciente (unicidad garantizada además por `UNIQUE(patient_id)` en la BD). */
    Optional<HistoriaClinica> findByPatientId(Long patientId);

    /** ¿Tiene el paciente Historia (en cualquier estado)? Sin cargar la entidad. */
    boolean existsByPatientId(Long patientId);

    /** {@code SELECT ... FOR UPDATE} para sellar (design.md D4 de `add-dental-chart`): dos sellados
     *  concurrentes se serializan y el segundo ve la Historia ya {@code COMPLETED}. Tiene que ser la
     *  primera carga de la Historia en la transacción: si ya estuviera en el contexto de persistencia,
     *  Hibernate devolvería esa instancia sin refrescar su estado. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HistoriaClinica h where h.patientId = :patientId")
    Optional<HistoriaClinica> lockByPatientId(@Param("patientId") Long patientId);
}
