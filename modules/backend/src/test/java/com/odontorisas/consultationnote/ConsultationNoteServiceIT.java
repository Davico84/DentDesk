package com.odontorisas.consultationnote;

import com.odontorisas.common.BackgroundCategory;
import com.odontorisas.common.BackgroundEntryKind;
import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.service.consultationnote.BackgroundEntryData;
import com.odontorisas.service.consultationnote.ClinicalBackgroundService;
import com.odontorisas.service.consultationnote.ClinicalHistoryNotCompletedException;
import com.odontorisas.service.consultationnote.ConsultationNoteAlreadyOpenException;
import com.odontorisas.service.consultationnote.ConsultationNoteChecklistIncompleteException;
import com.odontorisas.service.consultationnote.ConsultationNoteData;
import com.odontorisas.service.consultationnote.ConsultationNoteNotEmptyException;
import com.odontorisas.service.consultationnote.ConsultationNoteNotFoundException;
import com.odontorisas.service.consultationnote.ConsultationNoteSealedException;
import com.odontorisas.service.consultationnote.ConsultationNoteView;
import com.odontorisas.service.consultationnote.InvalidAttendedAtException;
import com.odontorisas.service.consultationnote.InvalidNextAppointmentException;
import com.odontorisas.service.consultationnote.PatientInactiveException;
import com.odontorisas.service.patient.PatientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenarios de la nota de consulta (`update-clinical-history`, spec de `clinical-history`): apertura,
 * contenido mínimo, fecha y hora de la atención, borrador descartable solo vacío y sellado
 * irreversible. Uno por WHEN/THEN; la concurrencia vive en {@code ConsultationNoteConcurrencyIT}.
 */
@SpringBootTest
class ConsultationNoteServiceIT extends AbstractConsultationNoteIT {

    @Autowired
    private PatientService patientService;
    @Autowired
    private ClinicalBackgroundService backgroundService;

    // --- Requirement: Nota de consulta posterior a la admisión ---

    @Test
    void opening_a_note_creates_it_open_for_the_patient_and_the_clinic() {
        Long patientId = aPatientWithSealedHistory();

        ConsultationNoteView note = noteService.open(patientId, hoursAgo(1));

        assertThat(note.status()).isEqualTo(ConsultationNoteStatus.ABIERTA);
        assertThat(note.patientId()).isEqualTo(patientId);
        assertThat(noteService.list(patientId)).extracting(ConsultationNoteView::id).containsExactly(note.id());
    }

    @Test
    void a_note_is_rejected_while_the_clinical_history_is_a_draft() {
        Long patientId = aPatient();
        clinicalHistoryService.create(patientId);

        assertThatThrownBy(() -> noteService.open(patientId, hoursAgo(1)))
            .isInstanceOf(ClinicalHistoryNotCompletedException.class);
    }

    @Test
    void a_note_is_rejected_when_the_patient_has_no_clinical_history() {
        Long patientId = aPatient();

        assertThatThrownBy(() -> noteService.open(patientId, hoursAgo(1)))
            .isInstanceOf(ClinicalHistoryNotCompletedException.class);
    }

    @Test
    void a_note_is_rejected_for_an_archived_patient() {
        Long patientId = aPatientWithSealedHistory();
        patientService.deactivate(patientId);

        assertThatThrownBy(() -> noteService.open(patientId, hoursAgo(1)))
            .isInstanceOf(PatientInactiveException.class);
    }

    @Test
    void a_second_open_note_is_rejected() {
        Long patientId = aPatientWithSealedHistory();
        noteService.open(patientId, hoursAgo(2));

        assertThatThrownBy(() -> noteService.open(patientId, hoursAgo(1)))
            .isInstanceOf(ConsultationNoteAlreadyOpenException.class);
    }

    @Test
    void a_note_of_another_clinic_is_not_found() throws Exception {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();

        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES ('Otra', 'otra-"
                 + UUID.randomUUID() + "') RETURNING id")) {
            rs.next();
            OrganizationContext.set(rs.getLong(1));
        }

        assertThatThrownBy(() -> noteService.get(patientId, noteId)).isInstanceOf(ConsultationNoteNotFoundException.class);
        assertThatThrownBy(() -> noteService.update(patientId, noteId, complete(hoursAgo(1))))
            .isInstanceOf(ConsultationNoteNotFoundException.class);
    }

    // --- Requirement: Contenido mínimo de la nota de consulta ---

    @Test
    void a_note_is_saved_with_only_part_of_its_content() {
        Long patientId = aPatientWithSealedHistory();
        Instant attendedAt = hoursAgo(1);
        Long noteId = noteService.open(patientId, attendedAt).id();

        ConsultationNoteView saved = noteService.update(patientId, noteId, new ConsultationNoteData(
            attendedAt, "Dolor al masticar", null, null, null, null, null, null, null, null, null));

        assertThat(saved.reason()).isEqualTo("Dolor al masticar");
        assertThat(saved.examination()).isNull();
        assertThat(saved.status()).isEqualTo(ConsultationNoteStatus.ABIERTA);
    }

    @Test
    void the_next_appointment_must_be_after_the_attention_day() {
        Long patientId = aPatientWithSealedHistory();
        Instant attendedAt = hoursAgo(1);
        Long noteId = noteService.open(patientId, attendedAt).id();
        LocalDate attentionDay = attendedAt.atZone(ZoneId.of("America/Lima")).toLocalDate();

        assertThatThrownBy(() -> noteService.update(patientId, noteId, withNextAppointment(attendedAt, attentionDay)))
            .isInstanceOf(InvalidNextAppointmentException.class);
        assertThatThrownBy(() -> noteService.update(patientId, noteId,
            withNextAppointment(attendedAt, attentionDay.minusDays(1))))
            .isInstanceOf(InvalidNextAppointmentException.class);

        assertThat(noteService.update(patientId, noteId, withNextAppointment(attendedAt, attentionDay.plusDays(7)))
            .nextAppointmentOn()).isEqualTo(attentionDay.plusDays(7));
    }

    private static ConsultationNoteData withNextAppointment(Instant attendedAt, LocalDate next) {
        return new ConsultationNoteData(attendedAt, "Control", null, null, null, null, null, null, null, null, next);
    }

    @Test
    void reading_a_note_returns_its_fields_diagnoses_and_background_entries() {
        Long patientId = aPatientWithSealedHistory();
        Instant attendedAt = hoursAgo(1);
        Long noteId = noteService.open(patientId, attendedAt).id();
        noteService.update(patientId, noteId, complete(attendedAt));
        noteDiagnosisService.add(patientId, noteId, diagnosis("K02", TipoDiagnostico.PRESUNTIVO));
        backgroundService.add(patientId, noteId,
            new BackgroundEntryData(BackgroundEntryKind.ALTA, BackgroundCategory.MEDICACION, "Losartán", null));

        ConsultationNoteView note = noteService.get(patientId, noteId);

        assertThat(note.examination()).isEqualTo("Encía sana");
        assertThat(note.diagnoses()).extracting(d -> d.cie10Code()).containsExactly("K02");
        assertThat(note.backgroundEntries()).extracting(e -> e.description()).containsExactly("Losartán");
    }

    // --- Requirement: Fecha y hora de la atención ---

    @Test
    void an_attention_earlier_that_day_keeps_its_time_and_records_when_it_was_written() {
        Long patientId = aPatientWithSealedHistory();
        Instant attendedAt = hoursAgo(3).truncatedTo(ChronoUnit.SECONDS);

        ConsultationNoteView note = noteService.open(patientId, attendedAt);

        assertThat(note.attendedAt()).isEqualTo(attendedAt);
        assertThat(note.createdAt()).isAfter(attendedAt);
        assertThat(note.createdBy()).isEqualTo(someUser);
    }

    @Test
    void an_attention_in_the_future_is_rejected() {
        Long patientId = aPatientWithSealedHistory();

        assertThatThrownBy(() -> noteService.open(patientId, Instant.now().plus(1, ChronoUnit.HOURS)))
            .isInstanceOf(InvalidAttendedAtException.class);
    }

    @Test
    void an_attention_before_the_last_sealed_note_is_rejected() {
        Long patientId = aPatientWithSealedHistory();
        aSealedNote(patientId, hoursAgo(5), "K02");

        assertThatThrownBy(() -> noteService.open(patientId, hoursAgo(10)))
            .isInstanceOf(InvalidAttendedAtException.class);
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();
        assertThatThrownBy(() -> noteService.update(patientId, noteId, complete(hoursAgo(10))))
            .isInstanceOf(InvalidAttendedAtException.class);
    }

    // --- Requirement: Nota abierta editable y descartable solo vacía ---

    @Test
    void an_open_note_can_be_edited_again() {
        Long patientId = aPatientWithSealedHistory();
        Instant attendedAt = hoursAgo(1);
        Long noteId = noteService.open(patientId, attendedAt).id();
        noteService.update(patientId, noteId, complete(attendedAt));

        ConsultationNoteView edited = noteService.update(patientId, noteId, new ConsultationNoteData(
            attendedAt, "Control", null, null, null, null, "Gingivitis leve", "Profilaxis", null, null, null));

        assertThat(edited.examination()).isEqualTo("Gingivitis leve");
        assertThat(edited.status()).isEqualTo(ConsultationNoteStatus.ABIERTA);
    }

    @Test
    void an_empty_open_note_can_be_discarded_and_another_opened() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = noteService.open(patientId, hoursAgo(2)).id();

        noteService.discard(patientId, noteId);

        assertThat(noteService.list(patientId)).isEmpty();
        assertThat(noteService.open(patientId, hoursAgo(1)).status()).isEqualTo(ConsultationNoteStatus.ABIERTA);
    }

    @Test
    void a_note_with_a_field_a_diagnosis_or_a_background_entry_cannot_be_discarded() {
        Long patientId = aPatientWithSealedHistory();
        Instant attendedAt = hoursAgo(1);
        Long noteId = noteService.open(patientId, attendedAt).id();

        noteService.update(patientId, noteId, new ConsultationNoteData(
            attendedAt, null, null, null, null, null, null, null, null, "Ortodoncia", null));
        assertThatThrownBy(() -> noteService.discard(patientId, noteId)).isInstanceOf(ConsultationNoteNotEmptyException.class);

        noteService.update(patientId, noteId, new ConsultationNoteData(
            attendedAt, null, null, null, null, null, null, null, null, null, null));
        Long diagnosisId = noteDiagnosisService.add(patientId, noteId, diagnosis("K02", TipoDiagnostico.PRESUNTIVO)).id();
        assertThatThrownBy(() -> noteService.discard(patientId, noteId)).isInstanceOf(ConsultationNoteNotEmptyException.class);

        noteDiagnosisService.remove(patientId, noteId, diagnosisId);
        backgroundService.add(patientId, noteId,
            new BackgroundEntryData(BackgroundEntryKind.ALTA, BackgroundCategory.ALERGIA_RAM, "Ibuprofeno", null));
        assertThatThrownBy(() -> noteService.discard(patientId, noteId)).isInstanceOf(ConsultationNoteNotEmptyException.class);

        assertThat(noteService.list(patientId)).hasSize(1);
    }

    // --- Requirement: Sellado irreversible de la nota ---

    @Test
    void sealing_a_complete_note_records_when_and_who() {
        Long patientId = aPatientWithSealedHistory();

        Long noteId = aSealedNote(patientId, hoursAgo(1), "K02");

        ConsultationNoteView note = noteService.get(patientId, noteId);
        assertThat(note.status()).isEqualTo(ConsultationNoteStatus.SELLADA);
        assertThat(note.sealedAt()).isNotNull();
        assertThat(note.sealedBy()).isEqualTo(someUser);
    }

    @Test
    void sealing_without_mandatory_content_lists_what_is_missing() {
        Long patientId = aPatientWithSealedHistory();
        Instant attendedAt = hoursAgo(1);
        Long noteId = noteService.open(patientId, attendedAt).id();
        noteService.update(patientId, noteId, new ConsultationNoteData(
            attendedAt, "Control", null, null, null, null, null, "Profilaxis", null, null, null));

        assertThatThrownBy(() -> noteService.seal(patientId, noteId))
            .isInstanceOfSatisfying(ConsultationNoteChecklistIncompleteException.class,
                e -> assertThat(e.getMissingFields()).containsExactly("examination", "diagnoses"));
        assertThat(noteService.get(patientId, noteId).status()).isEqualTo(ConsultationNoteStatus.ABIERTA);
    }

    @Test
    void a_sealed_note_rejects_every_change() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = aSealedNote(patientId, hoursAgo(1), "K02");
        Long diagnosisId = noteService.get(patientId, noteId).diagnoses().getFirst().id();

        assertThatThrownBy(() -> noteService.update(patientId, noteId, complete(hoursAgo(1))))
            .isInstanceOf(ConsultationNoteSealedException.class);
        assertThatThrownBy(() -> noteDiagnosisService.add(patientId, noteId, diagnosis("K05", TipoDiagnostico.PRESUNTIVO)))
            .isInstanceOf(ConsultationNoteSealedException.class);
        assertThatThrownBy(() -> noteDiagnosisService.remove(patientId, noteId, diagnosisId))
            .isInstanceOf(ConsultationNoteSealedException.class);
        assertThatThrownBy(() -> backgroundService.add(patientId, noteId,
            new BackgroundEntryData(BackgroundEntryKind.ALTA, BackgroundCategory.MEDICACION, "X", null)))
            .isInstanceOf(ConsultationNoteSealedException.class);
        assertThatThrownBy(() -> noteService.discard(patientId, noteId)).isInstanceOf(ConsultationNoteSealedException.class);

        assertThat(noteService.get(patientId, noteId).examination()).isEqualTo("Encía sana");
    }

    @Test
    void a_second_sealing_is_rejected_and_keeps_the_original() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = aSealedNote(patientId, hoursAgo(1), "K02");
        Instant sealedAt = noteService.get(patientId, noteId).sealedAt();

        assertThatThrownBy(() -> noteService.seal(patientId, noteId)).isInstanceOf(ConsultationNoteSealedException.class);
        assertThat(noteService.get(patientId, noteId).sealedAt()).isEqualTo(sealedAt);
    }
}
