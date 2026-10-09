package com.odontorisas.consultationnote;

import com.odontorisas.common.NoteTextField;
import com.odontorisas.service.consultationnote.ConsultationNoteCorrectionService;
import com.odontorisas.service.consultationnote.ConsultationNoteNotSealedException;
import com.odontorisas.service.consultationnote.ConsultationNoteView;
import com.odontorisas.service.consultationnote.CorrectionData;
import com.odontorisas.service.consultationnote.CorrectionNotAuthorException;
import com.odontorisas.service.consultationnote.CorrectionOfOmissionException;
import com.odontorisas.service.consultationnote.CorrectionView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenarios de <i>Corrección de un error material como registro nuevo</i> (design.md D6, DS
 * 009-2017-SA art. 47 c). La corrección de un diagnóstico no tiene ruta en el servicio (no existe en
 * {@link NoteTextField}); su rechazo por la API está en {@code ConsultationNoteControllerIT}. El
 * motivo vacío lo rechaza Bean Validation en el DTO y, como última barrera, el CHECK de V21.
 */
@SpringBootTest
class ConsultationNoteCorrectionServiceIT extends AbstractConsultationNoteIT {

    @Autowired
    private ConsultationNoteCorrectionService correctionService;

    private static final CorrectionData EXAM_FIX =
        new CorrectionData(NoteTextField.EXAMINATION, "Encía sana; caries en 37", "Omití anotar la pieza 37");

    @Test
    void the_author_corrects_a_sealed_note_and_the_original_stays_intact() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = aSealedNote(patientId, hoursAgo(1), "K02");

        CorrectionView correction = correctionService.add(patientId, noteId, EXAM_FIX);

        assertThat(correction.createdBy()).isEqualTo(someUser);
        assertThat(correction.createdAt()).isNotNull();
        ConsultationNoteView note = noteService.get(patientId, noteId);
        assertThat(note.examination()).isEqualTo("Encía sana");
        assertThat(note.corrections()).extracting(CorrectionView::correctedText).containsExactly("Encía sana; caries en 37");
    }

    @Test
    void another_user_cannot_correct_the_note() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = aSealedNote(patientId, hoursAgo(1), "K02");
        asUser(otherUser);

        assertThatThrownBy(() -> correctionService.add(patientId, noteId, EXAM_FIX))
            .isInstanceOf(CorrectionNotAuthorException.class);
    }

    @Test
    void a_field_that_was_empty_cannot_be_corrected() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = aSealedNote(patientId, hoursAgo(1), "K02");

        assertThatThrownBy(() -> correctionService.add(patientId, noteId,
            new CorrectionData(NoteTextField.REFERRAL, "Ortodoncia", "Faltó la referencia")))
            .isInstanceOf(CorrectionOfOmissionException.class);
    }

    @Test
    void an_open_note_cannot_be_corrected() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();
        noteService.update(patientId, noteId, complete(hoursAgo(1)));

        assertThatThrownBy(() -> correctionService.add(patientId, noteId, EXAM_FIX))
            .isInstanceOf(ConsultationNoteNotSealedException.class);
    }
}
