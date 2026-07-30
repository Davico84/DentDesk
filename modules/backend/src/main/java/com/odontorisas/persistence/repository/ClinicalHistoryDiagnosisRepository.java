package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.ClinicalHistoryDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso a {@link ClinicalHistoryDiagnosis}. Aislada por tenant automáticamente, igual que
 * {@link HistoriaClinicaRepository}.
 */
public interface ClinicalHistoryDiagnosisRepository extends JpaRepository<ClinicalHistoryDiagnosis, Long> {

    List<ClinicalHistoryDiagnosis> findByHistoriaId(Long historiaId);

    long countByHistoriaId(Long historiaId);
}
