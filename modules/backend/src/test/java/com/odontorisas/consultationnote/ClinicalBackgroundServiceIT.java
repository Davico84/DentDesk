package com.odontorisas.consultationnote;

import com.odontorisas.common.BackgroundCategory;
import com.odontorisas.common.BackgroundEntryKind;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.service.consultationnote.ActiveBackgroundView;
import com.odontorisas.service.consultationnote.ActiveBackgroundView.Source;
import com.odontorisas.service.consultationnote.BackgroundEntryData;
import com.odontorisas.service.consultationnote.BackgroundEntryNotActiveException;
import com.odontorisas.service.consultationnote.BackgroundEntryView;
import com.odontorisas.service.consultationnote.ClinicalBackgroundService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Scenarios de <i>Antecedentes nuevos como asientos de alta y baja</i> y <i>Antecedentes vigentes
 * del paciente</i> (design.md D7/D8, ADR-0020 D6). La categoría o descripción ausentes del alta las
 * rechaza Bean Validation (ver {@code ConsultationNoteControllerIT}) y el CHECK de V21.
 */
@SpringBootTest
class ClinicalBackgroundServiceIT extends AbstractConsultationNoteIT {

    @Autowired
    private ClinicalBackgroundService backgroundService;

    private static BackgroundEntryData alta(BackgroundCategory category, String description) {
        return new BackgroundEntryData(BackgroundEntryKind.ALTA, category, description, null);
    }

    private static BackgroundEntryData baja(Long closes) {
        return new BackgroundEntryData(BackgroundEntryKind.BAJA, null, "Suspendido", closes);
    }

    /** Abre una nota con una entrada, la completa y la sella. Devuelve la entrada. */
    private BackgroundEntryView inSealedNote(Long patientId, Instant attendedAt, BackgroundEntryData data) {
        Long noteId = noteService.open(patientId, attendedAt).id();
        BackgroundEntryView entry = backgroundService.add(patientId, noteId, data);
        noteService.update(patientId, noteId, complete(attendedAt));
        noteDiagnosisService.add(patientId, noteId, diagnosis("K02", TipoDiagnostico.PRESUNTIVO));
        noteService.seal(patientId, noteId);
        return entry;
    }

    // --- Requirement: Antecedentes nuevos como asientos de alta y baja ---

    @Test
    void a_new_allergy_is_recorded_in_the_note_with_its_author() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();

        BackgroundEntryView entry = backgroundService.add(patientId, noteId, alta(BackgroundCategory.ALERGIA_RAM, "Amoxicilina"));

        assertThat(entry.noteId()).isEqualTo(noteId);
        assertThat(entry.kind()).isEqualTo(BackgroundEntryKind.ALTA);
        assertThat(entry.createdBy()).isEqualTo(someUser);
    }

    @Test
    void closing_a_medication_adds_an_entry_and_keeps_the_original() {
        Long patientId = aPatientWithSealedHistory();
        BackgroundEntryView alta = inSealedNote(patientId, hoursAgo(5), alta(BackgroundCategory.MEDICACION, "Warfarina"));
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();

        BackgroundEntryView baja = backgroundService.add(patientId, noteId, baja(alta.id()));

        assertThat(baja.kind()).isEqualTo(BackgroundEntryKind.BAJA);
        assertThat(baja.category()).isEqualTo(BackgroundCategory.MEDICACION);
        assertThat(noteService.get(patientId, alta.noteId()).backgroundEntries())
            .extracting(BackgroundEntryView::description).containsExactly("Warfarina");
    }

    @Test
    void an_entry_already_closed_cannot_be_closed_again() {
        Long patientId = aPatientWithSealedHistory();
        BackgroundEntryView alta = inSealedNote(patientId, hoursAgo(5), alta(BackgroundCategory.MEDICACION, "Warfarina"));
        inSealedNote(patientId, hoursAgo(3), baja(alta.id()));
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();

        assertThatThrownBy(() -> backgroundService.add(patientId, noteId, baja(alta.id())))
            .isInstanceOf(BackgroundEntryNotActiveException.class);
    }

    @Test
    void an_entry_of_another_patient_cannot_be_closed() {
        Long other = aPatientWithSealedHistory();
        BackgroundEntryView foreign = inSealedNote(other, hoursAgo(5), alta(BackgroundCategory.MEDICACION, "Warfarina"));
        Long patientId = aPatientWithSealedHistory();
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();

        assertThatThrownBy(() -> backgroundService.add(patientId, noteId, baja(foreign.id())))
            .isInstanceOf(BackgroundEntryNotActiveException.class);
    }

    @Test
    void an_entry_still_in_an_open_note_cannot_be_closed() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();
        BackgroundEntryView draft = backgroundService.add(patientId, noteId, alta(BackgroundCategory.MEDICACION, "Warfarina"));

        assertThatThrownBy(() -> backgroundService.add(patientId, noteId, baja(draft.id())))
            .isInstanceOf(BackgroundEntryNotActiveException.class);
    }

    // --- Requirement: Antecedentes vigentes del paciente ---

    @Test
    void active_background_joins_the_three_sources_with_their_origin() {
        Long patientId = aPatientWithSealedHistory();
        inSealedNote(patientId, hoursAgo(2), alta(BackgroundCategory.ALERGIA_RAM, "Amoxicilina"));

        ActiveBackgroundView active = backgroundService.active(patientId);

        assertThat(active.items())
            .filteredOn(i -> i.category() == BackgroundCategory.ALERGIA_RAM)
            .extracting(ActiveBackgroundView.Item::source, ActiveBackgroundView.Item::description)
            .containsExactly(
                tuple(Source.HISTORIA, "Penicilina"),
                tuple(Source.NOTA, "Amoxicilina"),
                tuple(Source.FICHA, "Látex"));
        assertThat(active.items()).filteredOn(i -> i.source() == Source.HISTORIA).allMatch(i -> i.recordedAt() != null);
        assertThat(active.items()).filteredOn(i -> i.source() == Source.NOTA).allMatch(i -> i.recordedAt() != null);
        assertThat(active.items()).filteredOn(i -> i.source() == Source.FICHA).allMatch(i -> i.recordedAt() == null);
    }

    @Test
    void a_closed_entry_leaves_the_active_view_but_stays_in_its_note() {
        Long patientId = aPatientWithSealedHistory();
        BackgroundEntryView alta = inSealedNote(patientId, hoursAgo(5), alta(BackgroundCategory.MEDICACION, "Warfarina"));
        inSealedNote(patientId, hoursAgo(3), baja(alta.id()));

        assertThat(backgroundService.active(patientId).items())
            .extracting(ActiveBackgroundView.Item::description).doesNotContain("Warfarina");
        assertThat(noteService.get(patientId, alta.noteId()).backgroundEntries())
            .extracting(BackgroundEntryView::description).contains("Warfarina");
    }

    @Test
    void entries_of_an_open_note_are_not_active_yet() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();
        backgroundService.add(patientId, noteId, alta(BackgroundCategory.ALERGIA_RAM, "Amoxicilina"));

        assertThat(backgroundService.active(patientId).items())
            .extracting(ActiveBackgroundView.Item::description).doesNotContain("Amoxicilina");
    }
}
