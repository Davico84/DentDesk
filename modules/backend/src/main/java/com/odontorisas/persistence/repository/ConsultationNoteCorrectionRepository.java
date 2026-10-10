package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.ConsultationNoteCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Acceso a {@link ConsultationNoteCorrection}. Solo inserción y lectura (V22). */
public interface ConsultationNoteCorrectionRepository extends JpaRepository<ConsultationNoteCorrection, Long> {

    List<ConsultationNoteCorrection> findByNoteIdOrderByIdAsc(Long noteId);

    List<ConsultationNoteCorrection> findByNoteIdInOrderByIdAsc(Collection<Long> noteIds);
}
