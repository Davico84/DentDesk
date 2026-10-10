package com.odontorisas.service.consultationnote;

import com.odontorisas.persistence.entity.ConsultationNote;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenido obligatorio para sellar la nota (design.md D3, ADR-0020 D1): motivo, examen,
 * tratamiento y al menos un diagnóstico. Los nombres son los de los campos del DTO, para que el
 * cliente marque cada uno.
 */
final class ConsultationNoteChecklist {

    private ConsultationNoteChecklist() {
    }

    static List<String> missingFields(ConsultationNote note, long diagnosisCount) {
        List<String> missing = new ArrayList<>();
        if (NoteTextFields.isBlank(note.getReason())) {
            missing.add("reason");
        }
        if (NoteTextFields.isBlank(note.getExamination())) {
            missing.add("examination");
        }
        if (NoteTextFields.isBlank(note.getTreatment())) {
            missing.add("treatment");
        }
        if (diagnosisCount == 0) {
            missing.add("diagnoses");
        }
        return missing;
    }
}
