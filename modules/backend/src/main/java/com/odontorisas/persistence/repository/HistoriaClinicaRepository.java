package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.HistoriaClinica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Acceso a {@link HistoriaClinica}. Todas las consultas quedan restringidas a la organización del
 * contexto <b>automáticamente</b> ({@code @TenantId} + Row-Level Security) — sin condición de
 * organización escrita a mano, mismo patrón que {@code PatientRepository}.
 */
public interface HistoriaClinicaRepository extends JpaRepository<HistoriaClinica, Long> {

    /** Una por paciente (unicidad garantizada además por `UNIQUE(patient_id)` en la BD). */
    Optional<HistoriaClinica> findByPatientId(Long patientId);
}
