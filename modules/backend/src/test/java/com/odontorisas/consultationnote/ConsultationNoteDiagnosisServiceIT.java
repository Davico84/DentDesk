package com.odontorisas.consultationnote;

import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.service.clinicalhistory.Cie10CodeNotFoundException;
import com.odontorisas.service.clinicalhistory.DiagnosisData;
import com.odontorisas.service.consultationnote.NoteDiagnosisView;
import com.odontorisas.service.consultationnote.RepeatedDiagnosisWithoutPriorException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenarios de <i>Diagnósticos de la nota de consulta</i> y <i>Diagnóstico repetitivo exige un
 * registro previo</i> (design.md D5, NTS 139 §4.2.9). Las validaciones de forma del DTO (tipo
 * ausente, FDI fuera del conjunto) se prueban en {@code ConsultationNoteControllerIT}.
 */
@SpringBootTest
class ConsultationNoteDiagnosisServiceIT extends AbstractConsultationNoteIT {

    private Long openNote(Long patientId) {
        return noteService.open(patientId, hoursAgo(1)).id();
    }

    // --- Requirement: Diagnósticos de la nota de consulta ---

    @Test
    void a_diagnosis_is_added_with_its_registration_time_and_author() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = openNote(patientId);
        Instant before = Instant.now();

        NoteDiagnosisView added = noteDiagnosisService.add(patientId, noteId,
            new DiagnosisData("K02", DiagnosisLocation.PIEZA, 36, TipoDiagnostico.PRESUNTIVO));

        assertThat(added.fdiNumber()).isEqualTo(36);
        assertThat(added.createdAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(added.createdBy()).isEqualTo(someUser);
    }

    @Test
    void a_code_outside_the_catalog_is_rejected() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = openNote(patientId);

        assertThatThrownBy(() -> noteDiagnosisService.add(patientId, noteId, diagnosis("ZZZ99", TipoDiagnostico.DEFINITIVO)))
            .isInstanceOf(Cie10CodeNotFoundException.class);
    }

    // --- Requirement: Diagnóstico repetitivo exige un registro previo ---

    @Test
    void a_repeated_diagnosis_of_a_code_in_the_clinical_history_is_accepted() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = openNote(patientId);

        NoteDiagnosisView added = noteDiagnosisService.add(patientId, noteId, diagnosis(HC_CODE, TipoDiagnostico.REPETIDO));

        assertThat(added.tipoDiagnostico()).isEqualTo(TipoDiagnostico.REPETIDO);
    }

    @Test
    void a_repeated_diagnosis_of_a_code_in_a_previous_sealed_note_is_accepted() {
        Long patientId = aPatientWithSealedHistory();
        aSealedNote(patientId, hoursAgo(5), "K02");
        Long noteId = openNote(patientId);

        NoteDiagnosisView added = noteDiagnosisService.add(patientId, noteId, diagnosis("K02", TipoDiagnostico.REPETIDO));

        assertThat(added.cie10Code()).isEqualTo("K02");
    }

    @Test
    void a_repeated_diagnosis_without_a_prior_one_is_rejected() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = openNote(patientId);

        assertThatThrownBy(() -> noteDiagnosisService.add(patientId, noteId, diagnosis("K05", TipoDiagnostico.REPETIDO)))
            .isInstanceOf(RepeatedDiagnosisWithoutPriorException.class);
    }

    @Test
    void the_same_open_note_does_not_count_as_a_prior_diagnosis() {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = openNote(patientId);
        noteDiagnosisService.add(patientId, noteId, diagnosis("K05", TipoDiagnostico.PRESUNTIVO));

        assertThatThrownBy(() -> noteDiagnosisService.add(patientId, noteId, diagnosis("K05", TipoDiagnostico.REPETIDO)))
            .isInstanceOf(RepeatedDiagnosisWithoutPriorException.class);
    }

    @Test
    void a_code_diagnosed_to_another_patient_does_not_count() {
        Long other = aPatientWithSealedHistory();
        aSealedNote(other, hoursAgo(5), "K05");
        Long patientId = aPatientWithSealedHistory();
        Long noteId = openNote(patientId);

        assertThatThrownBy(() -> noteDiagnosisService.add(patientId, noteId, diagnosis("K05", TipoDiagnostico.REPETIDO)))
            .isInstanceOf(RepeatedDiagnosisWithoutPriorException.class);
    }
}
