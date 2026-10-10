package com.odontorisas.persistence.repository;

import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.persistence.entity.ConsultationNote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@link ConsultationNote}. Restringido a la organización del contexto automáticamente
 * (RLS + {@code @TenantId}), mismo patrón que {@link HistoriaClinicaRepository}.
 */
public interface ConsultationNoteRepository extends JpaRepository<ConsultationNote, Long> {

    /** Las notas del paciente en orden cronológico de atención. */
    List<ConsultationNote> findByPatientIdOrderByAttendedAtAscIdAsc(Long patientId);

    Optional<ConsultationNote> findByIdAndPatientId(Long id, Long patientId);

    /** La nota abierta del paciente, si existe (única por el índice {@code ux_consultation_note_open}). */
    Optional<ConsultationNote> findByPatientIdAndStatus(Long patientId, ConsultationNoteStatus status);

    /**
     * {@code SELECT ... FOR UPDATE} de la nota. Toda escritura de la nota o de lo que cuelga de ella
     * pasa por acá antes de comprobar que siga {@code ABIERTA} (docs/backend.md §8.3.1): sin el
     * bloqueo, una escritura que leyó {@code ABIERTA} se cuela durante el sellado — y como Hibernate
     * actualiza todas las columnas, guardar la nota con ese estado viejo la reabriría. Tiene que ser
     * la primera carga de la nota en la transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from ConsultationNote n where n.id = :id and n.patientId = :patientId")
    Optional<ConsultationNote> lockByIdAndPatientId(@Param("id") Long id, @Param("patientId") Long patientId);

    /** La atención más reciente ya sellada del paciente: ninguna nota puede quedar antes (design.md D4). */
    @Query("select max(n.attendedAt) from ConsultationNote n where n.patientId = :patientId "
        + "and n.status = com.odontorisas.common.ConsultationNoteStatus.SELLADA")
    Optional<Instant> findLatestSealedAttendedAt(@Param("patientId") Long patientId);
}
