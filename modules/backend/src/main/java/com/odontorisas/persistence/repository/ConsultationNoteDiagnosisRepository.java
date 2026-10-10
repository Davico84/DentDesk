package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.ConsultationNoteDiagnosis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Acceso a {@link ConsultationNoteDiagnosis}. Aislada por tenant automáticamente. */
public interface ConsultationNoteDiagnosisRepository extends JpaRepository<ConsultationNoteDiagnosis, Long> {

    List<ConsultationNoteDiagnosis> findByNoteIdOrderByIdAsc(Long noteId);

    List<ConsultationNoteDiagnosis> findByNoteIdInOrderByIdAsc(Collection<Long> noteIds);

    Optional<ConsultationNoteDiagnosis> findByIdAndNoteId(Long id, Long noteId);

    long countByNoteId(Long noteId);

    /** ¿Se registró este código en alguna nota <b>sellada</b> del paciente? (regla del R, design.md D5) */
    @Query("select count(d) > 0 from ConsultationNoteDiagnosis d, ConsultationNote n "
        + "where d.noteId = n.id and n.patientId = :patientId and d.cie10Code = :code "
        + "and n.status = com.odontorisas.common.ConsultationNoteStatus.SELLADA")
    boolean existsInSealedNoteOfPatient(@Param("patientId") Long patientId, @Param("code") String code);
}
