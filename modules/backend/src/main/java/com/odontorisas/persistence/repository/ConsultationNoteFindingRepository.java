package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.ConsultationNoteFinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Acceso a {@link ConsultationNoteFinding}. Aislada por tenant automáticamente. */
public interface ConsultationNoteFindingRepository extends JpaRepository<ConsultationNoteFinding, Long> {

    List<ConsultationNoteFinding> findByNoteIdOrderByIdAsc(Long noteId);

    List<ConsultationNoteFinding> findByNoteIdInOrderByIdAsc(Collection<Long> noteIds);

    Optional<ConsultationNoteFinding> findByIdAndNoteId(Long id, Long noteId);

    boolean existsByNoteIdAndFindingId(Long noteId, Long findingId);

    long countByNoteId(Long noteId);
}
