package com.odontorisas.service.consultationnote;

import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.ConsultationNote;
import com.odontorisas.persistence.entity.OrganizationSettings;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.ClinicalBackgroundEntryRepository;
import com.odontorisas.persistence.repository.ConsultationNoteCorrectionRepository;
import com.odontorisas.persistence.repository.ConsultationNoteDiagnosisRepository;
import com.odontorisas.persistence.repository.ConsultationNoteFindingRepository;
import com.odontorisas.persistence.repository.ConsultationNoteRepository;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import com.odontorisas.persistence.repository.OrganizationSettingsRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.patient.PatientNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Apertura, guardado, descarte y sellado de la nota de consulta (`update-clinical-history`
 * design.md D1â€“D4, ADR-0020).
 *
 * <p>Toda escritura de la nota o de lo que cuelga de ella pasa por {@link #lockOpenNote}: bloquear
 * y <b>despuÃ©s</b> comprobar {@code ABIERTA} (docs/backend.md Â§8.3.1).
 *
 * <p>âš ï¸ <b>Punto de extensiÃ³n del corte B</b> ({@code update-dental-chart}): el odontograma de
 * evoluciÃ³n se sella <b>junto con</b> la nota (ADR-0012 D5). Cuando llegue, {@link #seal} pasa a
 * ser llamado por un coordinador, como {@code CloseAdmissionService} con la Historia.
 */
@Service
@Transactional
public class ConsultationNoteService {

    /** Margen para el reloj del navegador: una atenciÃ³n "de ahora" puede llegar unos segundos adelantada. */
    static final Duration CLOCK_SKEW = Duration.ofMinutes(1);

    private final ConsultationNoteRepository noteRepository;
    private final ConsultationNoteDiagnosisRepository diagnosisRepository;
    private final ClinicalBackgroundEntryRepository entryRepository;
    private final ConsultationNoteCorrectionRepository correctionRepository;
    private final ConsultationNoteFindingRepository noteFindingRepository;
    private final HistoriaClinicaRepository historiaClinicaRepository;
    private final PatientRepository patientRepository;
    private final OrganizationSettingsRepository organizationSettingsRepository;
    private final Clock clock;

    public ConsultationNoteService(ConsultationNoteRepository noteRepository,
                                   ConsultationNoteDiagnosisRepository diagnosisRepository,
                                   ClinicalBackgroundEntryRepository entryRepository,
                                   ConsultationNoteCorrectionRepository correctionRepository,
                                   ConsultationNoteFindingRepository noteFindingRepository,
                                   HistoriaClinicaRepository historiaClinicaRepository,
                                   PatientRepository patientRepository,
                                   OrganizationSettingsRepository organizationSettingsRepository,
                                   Clock clock) {
        this.noteRepository = noteRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.entryRepository = entryRepository;
        this.correctionRepository = correctionRepository;
        this.noteFindingRepository = noteFindingRepository;
        this.historiaClinicaRepository = historiaClinicaRepository;
        this.patientRepository = patientRepository;
        this.organizationSettingsRepository = organizationSettingsRepository;
        this.clock = clock;
    }

    /**
     * Abre una nota {@code ABIERTA} (design.md D1). Exige paciente activo, Historia {@code COMPLETED}
     * â€”irreversible, asÃ­ que alcanza con leerlaâ€” y que no haya otra nota abierta: por pre-chequeo en
     * la vÃ­a feliz y por el Ã­ndice parcial {@code ux_consultation_note_open} si dos aperturas se cruzan.
     */
    public ConsultationNoteView open(Long patientId, Instant attendedAt) {
        Patient patient = patientRepository.findById(patientId).orElseThrow(PatientNotFoundException::new);
        if (patient.getStatus() != PatientStatus.ACTIVE) {
            throw new PatientInactiveException();
        }
        boolean completed = historiaClinicaRepository.findByPatientId(patientId)
            .map(h -> h.getStatus() == HistoriaClinicaStatus.COMPLETED)
            .orElse(false);
        if (!completed) {
            throw new ClinicalHistoryNotCompletedException();
        }
        if (noteRepository.findByPatientIdAndStatus(patientId, ConsultationNoteStatus.ABIERTA).isPresent()) {
            throw new ConsultationNoteAlreadyOpenException();
        }
        requireValidAttendedAt(patientId, attendedAt);

        ConsultationNote note = ConsultationNote.builder()
            .patientId(patientId)
            .status(ConsultationNoteStatus.ABIERTA)
            .attendedAt(attendedAt)
            .build();
        try {
            return toView(noteRepository.saveAndFlush(note));
        } catch (DataIntegrityViolationException raceLostToAnotherOpening) {
            throw new ConsultationNoteAlreadyOpenException();
        }
    }

    @Transactional(readOnly = true)
    public List<ConsultationNoteView> list(Long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new PatientNotFoundException();
        }
        List<ConsultationNote> notes = noteRepository.findByPatientIdOrderByAttendedAtAscIdAsc(patientId);
        List<Long> ids = notes.stream().map(ConsultationNote::getId).toList();
        Map<Long, List<NoteDiagnosisView>> diagnoses = byNote(
            diagnosisRepository.findByNoteIdInOrderByIdAsc(ids).stream().map(NoteDiagnosisView::from).toList(),
            NoteDiagnosisView::noteId);
        Map<Long, List<BackgroundEntryView>> entries = byNote(
            entryRepository.findByNoteIdInOrderByIdAsc(ids).stream().map(BackgroundEntryView::from).toList(),
            BackgroundEntryView::noteId);
        Map<Long, List<CorrectionView>> corrections = byNote(
            correctionRepository.findByNoteIdInOrderByIdAsc(ids).stream().map(CorrectionView::from).toList(),
            CorrectionView::noteId);
        Map<Long, List<NoteFindingView>> attended = byNote(
            noteFindingRepository.findByNoteIdInOrderByIdAsc(ids).stream().map(NoteFindingView::from).toList(),
            NoteFindingView::noteId);
        return notes.stream()
            .map(n -> ConsultationNoteView.from(n,
                diagnoses.getOrDefault(n.getId(), List.of()),
                entries.getOrDefault(n.getId(), List.of()),
                corrections.getOrDefault(n.getId(), List.of()),
                attended.getOrDefault(n.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public ConsultationNoteView get(Long patientId, Long noteId) {
        return toView(noteRepository.findByIdAndPatientId(noteId, patientId)
            .orElseThrow(ConsultationNoteNotFoundException::new));
    }

    /** Guarda el contenido de la nota abierta; reemplaza todos los campos (design.md D3/D4). */
    public ConsultationNoteView update(Long patientId, Long noteId, ConsultationNoteData data) {
        ConsultationNote note = lockOpenNote(noteRepository, patientId, noteId);
        requireValidAttendedAt(patientId, data.attendedAt());
        requireValidNextAppointment(data);

        note.setAttendedAt(data.attendedAt());
        note.setReason(data.reason());
        note.setIllnessDuration(data.illnessDuration());
        note.setSymptomsAndSigns(data.symptomsAndSigns());
        note.setPreviousTreatment(data.previousTreatment());
        note.setBiologicalFunctions(data.biologicalFunctions());
        note.setExamination(data.examination());
        note.setTreatment(data.treatment());
        note.setAuxiliaryExams(data.auxiliaryExams());
        note.setReferral(data.referral());
        note.setNextAppointmentOn(data.nextAppointmentOn());
        return toView(noteRepository.save(note));
    }

    /** Descarta la nota abierta, solo si estÃ¡ vacÃ­a (design.md D2). */
    public void discard(Long patientId, Long noteId) {
        ConsultationNote note = lockOpenNote(noteRepository, patientId, noteId);
        boolean empty = NoteTextFields.hasNoContent(note)
            && diagnosisRepository.countByNoteId(noteId) == 0
            && entryRepository.countByNoteId(noteId) == 0
            && noteFindingRepository.countByNoteId(noteId) == 0;
        if (!empty) {
            throw new ConsultationNoteNotEmptyException();
        }
        noteRepository.delete(note);
    }

    /**
     * Sella la nota (design.md D2): unidireccional, con el contenido obligatorio (D3) y la fecha de
     * atenciÃ³n revalidada â€” entre que se abriÃ³ y se sella pudo sellarse otra con una atenciÃ³n
     * posterior. Quien sella queda como firmante: es el Ãºnico que podrÃ¡ corregirla (D6).
     */
    public ConsultationNoteView seal(Long patientId, Long noteId) {
        ConsultationNote note = lockOpenNote(noteRepository, patientId, noteId);
        List<String> missing = ConsultationNoteChecklist.missingFields(note, diagnosisRepository.countByNoteId(noteId));
        if (!missing.isEmpty()) {
            throw new ConsultationNoteChecklistIncompleteException(missing);
        }
        requireValidAttendedAt(patientId, note.getAttendedAt());

        note.setStatus(ConsultationNoteStatus.SELLADA);
        note.setSealedAt(Instant.now(clock));
        note.setSealedBy(IdentityContext.get());
        return toView(noteRepository.save(note));
    }

    /**
     * La puerta de toda escritura de la nota y de lo que cuelga de ella: {@code SELECT ... FOR UPDATE}
     * y <b>despuÃ©s</b> exige {@code ABIERTA}. âš ï¸ Tiene que ser la primera carga de la nota en la
     * transacciÃ³n (docs/backend.md Â§8.3.1).
     */
    static ConsultationNote lockOpenNote(ConsultationNoteRepository repository, Long patientId, Long noteId) {
        ConsultationNote note = repository.lockByIdAndPatientId(noteId, patientId)
            .orElseThrow(ConsultationNoteNotFoundException::new);
        if (note.getStatus() != ConsultationNoteStatus.ABIERTA) {
            throw new ConsultationNoteSealedException();
        }
        return note;
    }

    /** No futura (con {@link #CLOCK_SKEW} de margen) ni anterior a la Ãºltima nota sellada (design.md D4). */
    private void requireValidAttendedAt(Long patientId, Instant attendedAt) {
        if (attendedAt.isAfter(Instant.now(clock).plus(CLOCK_SKEW))) {
            throw InvalidAttendedAtException.future();
        }
        noteRepository.findLatestSealedAttendedAt(patientId)
            .filter(attendedAt::isBefore)
            .ifPresent(latest -> {
                throw InvalidAttendedAtException.beforeLastSealed();
            });
    }

    /** La prÃ³xima cita es posterior al <b>dÃ­a</b> de la atenciÃ³n, en la zona horaria de la clÃ­nica (design.md D3). */
    private void requireValidNextAppointment(ConsultationNoteData data) {
        if (data.nextAppointmentOn() == null) {
            return;
        }
        if (!data.nextAppointmentOn().isAfter(data.attendedAt().atZone(clinicZone()).toLocalDate())) {
            throw new InvalidNextAppointmentException();
        }
    }

    private ZoneId clinicZone() {
        String timezone = organizationSettingsRepository.findById(OrganizationContext.get())
            .map(OrganizationSettings::getTimezone)
            .orElse(OrganizationSettings.DEFAULT_TIMEZONE);
        return ZoneId.of(timezone);
    }

    private ConsultationNoteView toView(ConsultationNote note) {
        Long id = note.getId();
        return ConsultationNoteView.from(note,
            diagnosisRepository.findByNoteIdOrderByIdAsc(id).stream().map(NoteDiagnosisView::from).toList(),
            entryRepository.findByNoteIdOrderByIdAsc(id).stream().map(BackgroundEntryView::from).toList(),
            correctionRepository.findByNoteIdOrderByIdAsc(id).stream().map(CorrectionView::from).toList(),
            noteFindingRepository.findByNoteIdOrderByIdAsc(id).stream().map(NoteFindingView::from).toList());
    }

    private static <T> Map<Long, List<T>> byNote(Collection<T> items, Function<T, Long> noteId) {
        return items.stream().collect(Collectors.groupingBy(noteId));
    }
}
