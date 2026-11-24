package com.odontorisas.service.appointment;

import com.odontorisas.common.AppointmentStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Appointment;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.entity.User;
import com.odontorisas.persistence.repository.AppointmentRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.persistence.repository.UserRepository;
import com.odontorisas.service.appointment.BusinessHoursService.ClinicSchedule;
import com.odontorisas.service.consultationnote.ConsultationNoteService;
import com.odontorisas.service.consultationnote.PatientInactiveException;
import com.odontorisas.service.patient.PatientNotFoundException;
import com.odontorisas.service.staff.ClinicalCapacityService;
import com.odontorisas.service.staff.ClinicalCapacityService.CapacityView;
import com.odontorisas.service.staff.ClinicalCapacityService.EnabledMember;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La agenda interna de la clínica (add-appointments design.md D2–D7).
 *
 * <p>Inicio y fin son instantes; lo civil —grilla de 5 minutos, "futura", día y horario de atención— se evalúa
 * en la zona horaria de la clínica ({@link BusinessHoursService#current()}). La capacidad del odontólogo, en
 * cambio, en la fecha de Perú, como toda vigencia de credencial ({@link ClinicalCapacityService#PERU}).
 *
 * <p>Toda transición y reprogramación bloquea la cita {@code FOR UPDATE} y comprueba el estado después
 * (D6). "Sin choques" lo garantiza la restricción de exclusión de V32; el pre-chequeo solo da el {@code 409}
 * con detalle en la vía feliz.
 */
@Service
@Transactional
public class AppointmentService {

    static final int MIN_DURATION = 10;
    static final int MAX_DURATION = 240;
    static final int GRID_MINUTES = 5;
    static final int MAX_RANGE_DAYS = 7;

    /** SQLState de una violación de restricción de exclusión. */
    private static final String EXCLUSION_VIOLATION = "23P01";

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final BusinessHoursService businessHoursService;
    private final ClinicalCapacityService clinicalCapacityService;
    private final ConsultationNoteService consultationNoteService;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointmentRepository, PatientRepository patientRepository,
                              UserRepository userRepository, BusinessHoursService businessHoursService,
                              ClinicalCapacityService clinicalCapacityService,
                              ConsultationNoteService consultationNoteService, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.businessHoursService = businessHoursService;
        this.clinicalCapacityService = clinicalCapacityService;
        this.consultationNoteService = consultationNoteService;
        this.clock = clock;
    }

    /** Lo que se agenda: paciente, odontólogo, inicio (instante), duración en minutos y motivo opcional. */
    public record ScheduleData(Long patientId, Long dentistUserId, Instant startsAt, int durationMinutes,
                               String reason) {
    }

    /** Lo que se reprograma: odontólogo, inicio y duración. La cita conserva su paciente (spec). */
    public record RescheduleData(Long dentistUserId, Instant startsAt, int durationMinutes) {
    }

    /** Citas cuyo inicio cae en los días civiles {@code from..to} (ambos inclusive, máximo 7) de la clínica. */
    @Transactional(readOnly = true)
    public List<AppointmentView> list(LocalDate from, LocalDate to, Long dentistUserId) {
        if (to.isBefore(from)) {
            throw new InvalidAppointmentScheduleException("to", "El fin del rango no puede ser anterior al inicio.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new InvalidAppointmentScheduleException("to", "El rango no puede superar los 7 días.");
        }
        ClinicSchedule schedule = businessHoursService.current();
        Instant start = from.atStartOfDay(schedule.zone()).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(schedule.zone()).toInstant();
        return toViews(appointmentRepository.findInRange(start, end, dentistUserId));
    }

    @Transactional(readOnly = true)
    public AppointmentView get(Long id) {
        return toViews(List.of(appointmentRepository.findById(id).orElseThrow(AppointmentNotFoundException::new)))
            .getFirst();
    }

    /** Odontólogos agendables: miembros con capacidad clínica vigente hoy (design.md D4). */
    @Transactional(readOnly = true)
    public List<EnabledMember> schedulableDentists() {
        return clinicalCapacityService.enabledMembersToday();
    }

    public AppointmentView schedule(ScheduleData data) {
        Patient patient = patientRepository.findById(data.patientId()).orElseThrow(PatientNotFoundException::new);
        if (patient.getStatus() != PatientStatus.ACTIVE) {
            throw new PatientInactiveException();
        }
        Instant endsAt = validateSlot(data.dentistUserId(), data.startsAt(), data.durationMinutes(), null);
        Appointment appointment = Appointment.builder()
            .patientId(patient.getId())
            .dentistUserId(data.dentistUserId())
            .startsAt(data.startsAt())
            .endsAt(endsAt)
            .status(AppointmentStatus.AGENDADA)
            .reason(blankToNull(data.reason()))
            .build();
        return toView(saveGuardingOverlap(appointment));
    }

    public AppointmentView reschedule(Long id, RescheduleData data) {
        Appointment appointment = lockOpen(id);
        Instant endsAt = validateSlot(data.dentistUserId(), data.startsAt(), data.durationMinutes(), id);
        appointment.setDentistUserId(data.dentistUserId());
        appointment.setStartsAt(data.startsAt());
        appointment.setEndsAt(endsAt);
        return toView(saveGuardingOverlap(appointment));
    }

    public AppointmentView cancel(Long id, String cancellationReason) {
        Appointment appointment = lockOpen(id);
        appointment.setCancellationReason(blankToNull(cancellationReason));
        return toView(close(appointment, AppointmentStatus.CANCELADA));
    }

    public AppointmentView markAttended(Long id) {
        Appointment appointment = lockOpen(id);
        requireStarted(appointment);
        return toView(close(appointment, AppointmentStatus.ATENDIDA));
    }

    public AppointmentView markNoShow(Long id) {
        Appointment appointment = lockOpen(id);
        requireStarted(appointment);
        return toView(close(appointment, AppointmentStatus.NO_ASISTIO));
    }

    /**
     * "Atender con nota" en una transacción (design.md D7). Orden de bloqueos: capacidad de quien llama
     * (membresía y credencial {@code FOR SHARE}) → cita {@code FOR UPDATE} → apertura de la nota. Si la nota no
     * se puede abrir, todo vuelve atrás con el error de la nota.
     */
    public AppointmentView attendWithNote(Long id) {
        clinicalCapacityService.require();
        Appointment appointment = appointmentRepository.findForUpdate(id).orElseThrow(AppointmentNotFoundException::new);
        if (appointment.getConsultationNoteId() != null) {
            throw new AppointmentNoteAlreadyLinkedException();
        }
        boolean scheduled = appointment.getStatus() == AppointmentStatus.AGENDADA;
        if (!scheduled && appointment.getStatus() != AppointmentStatus.ATENDIDA) {
            throw new AppointmentClosedException();
        }
        if (scheduled) {
            requireStarted(appointment);
        }
        Long noteId = consultationNoteService.open(appointment.getPatientId(), appointment.getStartsAt()).id();
        appointment.setConsultationNoteId(noteId);
        if (scheduled) {
            markStatusChange(appointment, AppointmentStatus.ATENDIDA);
        }
        return toView(appointmentRepository.saveAndFlush(appointment));
    }

    // --- Reglas ---

    /**
     * Valida el hueco y devuelve el fin. Orden: grilla y duración (400) → futura (422) → odontólogo con capacidad
     * en la fecha (409) → horario de atención (422) → choque (409), sin contar {@code excludedId}.
     */
    private Instant validateSlot(Long dentistUserId, Instant startsAt, int durationMinutes, Long excludedId) {
        if (durationMinutes < MIN_DURATION || durationMinutes > MAX_DURATION || durationMinutes % GRID_MINUTES != 0) {
            throw new InvalidAppointmentScheduleException("durationMinutes",
                "La duración debe ser múltiplo de 5 minutos, entre 10 y 240.");
        }
        ClinicSchedule schedule = businessHoursService.current();
        ZonedDateTime civilStart = startsAt.atZone(schedule.zone());
        if (civilStart.getMinute() % GRID_MINUTES != 0 || civilStart.getSecond() != 0 || civilStart.getNano() != 0) {
            throw new InvalidAppointmentScheduleException("startsAt", "La hora de inicio debe ser múltiplo de 5 minutos.");
        }
        if (!startsAt.isAfter(clock.instant())) {
            throw new AppointmentInPastException();
        }
        Instant endsAt = startsAt.plus(Duration.ofMinutes(durationMinutes));
        requireSchedulable(dentistUserId, startsAt.atZone(ClinicalCapacityService.PERU).toLocalDate());
        ZonedDateTime civilEnd = endsAt.atZone(schedule.zone());
        if (!schedule.hours().contains(civilStart.toLocalDateTime(), civilEnd.toLocalDateTime())) {
            throw new OutsideBusinessHoursException(schedule.hours().isClosed(civilStart.getDayOfWeek()));
        }
        if (appointmentRepository.existsOverlap(dentistUserId, startsAt, endsAt, excludedId)) {
            throw new AppointmentConflictException();
        }
        return endsAt;
    }

    private void requireSchedulable(Long dentistUserId, LocalDate appointmentDateInPeru) {
        CapacityView capacity = clinicalCapacityService.onDate(dentistUserId, appointmentDateInPeru)
            .orElseThrow(DentistNotSchedulableException::notAMember);
        if (!capacity.enabled()) {
            throw new DentistNotSchedulableException(capacity.state(), capacity.licensedUntil());
        }
    }

    private Appointment lockOpen(Long id) {
        Appointment appointment = appointmentRepository.findForUpdate(id).orElseThrow(AppointmentNotFoundException::new);
        if (appointment.getStatus().isFinal()) {
            throw new AppointmentClosedException();
        }
        return appointment;
    }

    private void requireStarted(Appointment appointment) {
        if (appointment.getStartsAt().isAfter(clock.instant())) {
            throw new AppointmentNotStartedException();
        }
    }

    private Appointment close(Appointment appointment, AppointmentStatus status) {
        markStatusChange(appointment, status);
        return appointmentRepository.saveAndFlush(appointment);
    }

    private void markStatusChange(Appointment appointment, AppointmentStatus status) {
        appointment.setStatus(status);
        appointment.setStatusChangedAt(clock.instant());
        appointment.setStatusChangedBy(IdentityContext.get());
    }

    /** Guarda y traduce la violación de la restricción de exclusión (dos reservas a la vez) al mismo 409. */
    private Appointment saveGuardingOverlap(Appointment appointment) {
        try {
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) {
                throw new AppointmentConflictException();
            }
            throw e;
        }
    }

    private static boolean isExclusionViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }

    // --- Vistas ---

    private AppointmentView toView(Appointment appointment) {
        return toViews(List.of(appointment)).getFirst();
    }

    /** Nombres de pacientes y usuarios en dos consultas, no una por cita. */
    private List<AppointmentView> toViews(List<Appointment> appointments) {
        Map<Long, String> patients = namesById(appointments.stream().map(Appointment::getPatientId).toList(),
            ids -> patientRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Patient::getId, Patient::getFullName)));
        Set<Long> userIds = new HashSet<>();
        appointments.forEach(a -> {
            userIds.add(a.getDentistUserId());
            if (a.getStatusChangedBy() != null) {
                userIds.add(a.getStatusChangedBy());
            }
        });
        Map<Long, String> users = namesById(userIds, ids -> userRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(User::getId, u -> u.getFullName() == null ? u.getEmail() : u.getFullName())));
        return appointments.stream().map(a -> AppointmentView.from(a, patients, users)).toList();
    }

    private static Map<Long, String> namesById(Collection<Long> ids, Function<Set<Long>, Map<Long, String>> load) {
        Set<Long> unique = new HashSet<>(ids);
        return unique.isEmpty() ? Map.of() : load.apply(unique);
    }
}
