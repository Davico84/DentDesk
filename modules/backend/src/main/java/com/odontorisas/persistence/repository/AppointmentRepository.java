package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a {@link Appointment}. Restringido a la organización del contexto automáticamente (RLS +
 * {@code @TenantId}).
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Citas que empiezan en {@code [from, to)}, opcionalmente de un odontólogo, en orden. La consulta semanal
     * (design.md D8) usa el índice {@code (organization_id, starts_at)}.
     */
    @Query("select a from Appointment a where a.startsAt >= :from and a.startsAt < :to "
        + "and (:dentistId is null or a.dentistUserId = :dentistId) order by a.startsAt, a.id")
    List<Appointment> findInRange(@Param("from") Instant from, @Param("to") Instant to,
                                  @Param("dentistId") Long dentistId);

    /**
     * {@code SELECT ... FOR UPDATE} de la cita (design.md D6): toda transición y reprogramación la toma antes
     * de comprobar el estado, así dos clics simultáneos no dejan dos cambios.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findForUpdate(@Param("id") Long id);

    /**
     * Si el odontólogo ya tiene una cita vigente ({@code AGENDADA} o {@code ATENDIDA}) que se solapa con
     * {@code [startsAt, endsAt)}, sin contar {@code excludedId}. Es el pre-chequeo del {@code 409} con detalle;
     * la garantía ante carreras es la restricción de exclusión de V32.
     */
    @Query("select count(a) > 0 from Appointment a where a.dentistUserId = :dentistId "
        + "and a.status in (com.odontorisas.common.AppointmentStatus.AGENDADA, "
        + "com.odontorisas.common.AppointmentStatus.ATENDIDA) "
        + "and a.startsAt < :endsAt and a.endsAt > :startsAt "
        + "and (:excludedId is null or a.id <> :excludedId)")
    boolean existsOverlap(@Param("dentistId") Long dentistId, @Param("startsAt") Instant startsAt,
                          @Param("endsAt") Instant endsAt, @Param("excludedId") Long excludedId);
}
