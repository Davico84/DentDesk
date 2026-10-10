package com.odontorisas.service.consultationnote;

import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.persistence.entity.ConsultationNote;
import com.odontorisas.persistence.entity.ConsultationNoteCorrection;
import com.odontorisas.persistence.repository.ConsultationNoteCorrectionRepository;
import com.odontorisas.persistence.repository.ConsultationNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corrección de un error material de una nota sellada como <b>registro nuevo</b> (design.md D6,
 * DS 009-2017-SA art. 47 c, NTS 139 §5.3.3 j). El original no se toca. No aplica a diagnósticos
 * (no hay forma de expresarlo: {@code NoteTextField} solo tiene campos de texto) ni a omisiones.
 */
@Service
@Transactional
public class ConsultationNoteCorrectionService {

    private final ConsultationNoteRepository noteRepository;
    private final ConsultationNoteCorrectionRepository correctionRepository;

    public ConsultationNoteCorrectionService(ConsultationNoteRepository noteRepository,
                                             ConsultationNoteCorrectionRepository correctionRepository) {
        this.noteRepository = noteRepository;
        this.correctionRepository = correctionRepository;
    }

    public CorrectionView add(Long patientId, Long noteId, CorrectionData data) {
        ConsultationNote note = noteRepository.findByIdAndPatientId(noteId, patientId)
            .orElseThrow(ConsultationNoteNotFoundException::new);
        if (note.getStatus() != ConsultationNoteStatus.SELLADA) {
            throw new ConsultationNoteNotSealedException();
        }
        if (!note.getSealedBy().equals(IdentityContext.get())) {
            throw new CorrectionNotAuthorException();
        }
        if (NoteTextFields.isBlank(NoteTextFields.valueOf(note, data.field()))) {
            throw new CorrectionOfOmissionException();
        }
        ConsultationNoteCorrection correction = ConsultationNoteCorrection.builder()
            .noteId(noteId)
            .field(data.field())
            .correctedText(data.correctedText())
            .reason(data.reason())
            .build();
        return CorrectionView.from(correctionRepository.save(correction));
    }
}
