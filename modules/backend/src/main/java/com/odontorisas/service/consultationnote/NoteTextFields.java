package com.odontorisas.service.consultationnote;

import com.odontorisas.common.NoteTextField;
import com.odontorisas.persistence.entity.ConsultationNote;

import java.util.Arrays;

/** Lectura de los campos de texto de una nota por {@link NoteTextField} (corrección y vacuidad). */
final class NoteTextFields {

    private NoteTextFields() {
    }

    static String valueOf(ConsultationNote n, NoteTextField field) {
        return switch (field) {
            case REASON -> n.getReason();
            case ILLNESS_DURATION -> n.getIllnessDuration();
            case SYMPTOMS_AND_SIGNS -> n.getSymptomsAndSigns();
            case PREVIOUS_TREATMENT -> n.getPreviousTreatment();
            case BIOLOGICAL_FUNCTIONS -> n.getBiologicalFunctions();
            case EXAMINATION -> n.getExamination();
            case TREATMENT -> n.getTreatment();
            case AUXILIARY_EXAMS -> n.getAuxiliaryExams();
            case REFERRAL -> n.getReferral();
        };
    }

    /** Vacío = nulo o solo espacios (design.md D3). */
    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** ¿No tiene la nota ningún campo de contenido? La próxima cita cuenta como contenido. */
    static boolean hasNoContent(ConsultationNote n) {
        return n.getNextAppointmentOn() == null
            && Arrays.stream(NoteTextField.values()).allMatch(f -> isBlank(valueOf(n, f)));
    }
}
