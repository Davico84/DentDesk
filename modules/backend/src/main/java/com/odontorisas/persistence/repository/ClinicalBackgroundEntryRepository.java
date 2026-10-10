package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.ClinicalBackgroundEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Acceso a {@link ClinicalBackgroundEntry}. Aislada por tenant automáticamente. */
public interface ClinicalBackgroundEntryRepository extends JpaRepository<ClinicalBackgroundEntry, Long> {

    List<ClinicalBackgroundEntry> findByNoteIdOrderByIdAsc(Long noteId);

    List<ClinicalBackgroundEntry> findByNoteIdInOrderByIdAsc(Collection<Long> noteIds);

    List<ClinicalBackgroundEntry> findByPatientIdOrderByIdAsc(Long patientId);

    Optional<ClinicalBackgroundEntry> findByIdAndNoteId(Long id, Long noteId);

    long countByNoteId(Long noteId);

    boolean existsByClosesEntryId(Long closesEntryId);
}
