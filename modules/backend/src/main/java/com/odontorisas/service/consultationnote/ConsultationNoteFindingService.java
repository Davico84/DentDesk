package com.odontorisas.service.consultationnote;

import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.persistence.entity.ConsultationNote;
import com.odontorisas.persistence.entity.ConsultationNoteFinding;
import com.odontorisas.persistence.entity.DentalChartBlock;
import com.odontorisas.persistence.entity.DentalChartFinding;
import com.odontorisas.persistence.repository.ConsultationNoteFindingRepository;
import com.odontorisas.persistence.repository.ConsultationNoteRepository;
import com.odontorisas.persistence.repository.DentalChartBlockRepository;
import com.odontorisas.persistence.repository.DentalChartFindingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hallazgos del odontograma atendidos en la nota (update-consultation-note-findings design.md D1).
 *
 * <p>Toda escritura pasa por {@link ConsultationNoteService#lockOpenNote}, como diagnósticos y
 * antecedentes: una marca que espera detrás del sellado recibe 409 al despertar, y la guarda de la
 * base (V29) cubre cualquier camino que no pase por acá.
 *
 * <p>"Mismo paciente" se comprueba acá: el hallazgo solo tiene {@code block_id}, y la base garantiza
 * la clínica (FK compuesta + RLS), no el paciente. Otro paciente responde igual que inexistente (404).
 */
@Service
@Transactional
public class ConsultationNoteFindingService {

    private final ConsultationNoteRepository noteRepository;
    private final ConsultationNoteFindingRepository noteFindingRepository;
    private final DentalChartFindingRepository findingRepository;
    private final DentalChartBlockRepository blockRepository;

    public ConsultationNoteFindingService(ConsultationNoteRepository noteRepository,
                                          ConsultationNoteFindingRepository noteFindingRepository,
                                          DentalChartFindingRepository findingRepository,
                                          DentalChartBlockRepository blockRepository) {
        this.noteRepository = noteRepository;
        this.noteFindingRepository = noteFindingRepository;
        this.findingRepository = findingRepository;
        this.blockRepository = blockRepository;
    }

    /** Marca un hallazgo como atendido en la nota abierta, con la descripción que compuso la UI (D2). */
    public NoteFindingView attend(Long patientId, Long noteId, Long findingId, String description) {
        ConsultationNote note = ConsultationNoteService.lockOpenNote(noteRepository, patientId, noteId);
        DentalChartFinding finding = findingRepository.findById(findingId)
            .orElseThrow(NoteFindingNotFoundException::new);
        DentalChartBlock block = blockRepository.findById(finding.getBlockId())
            .filter(b -> b.getPatientId().equals(patientId))
            .orElseThrow(NoteFindingNotFoundException::new);
        if (block.getStatus() != DentalChartBlockStatus.SEALED) {
            throw new DentalChartFindingNotSealedException();
        }
        if (noteFindingRepository.existsByNoteIdAndFindingId(note.getId(), findingId)) {
            throw new NoteFindingAlreadyAttendedException();
        }
        ConsultationNoteFinding attended = ConsultationNoteFinding.builder()
            .noteId(note.getId())
            .findingId(findingId)
            .description(description.strip())
            .build();
        try {
            return NoteFindingView.from(noteFindingRepository.saveAndFlush(attended));
        } catch (DataIntegrityViolationException duplicateRace) {
            // Con la nota bloqueada no debería haber carrera; el UNIQUE es la red de seguridad.
            throw new NoteFindingAlreadyAttendedException();
        }
    }

    /** Desmarca un hallazgo de la nota abierta. */
    public void remove(Long patientId, Long noteId, Long noteFindingId) {
        ConsultationNoteService.lockOpenNote(noteRepository, patientId, noteId);
        ConsultationNoteFinding attended = noteFindingRepository.findByIdAndNoteId(noteFindingId, noteId)
            .orElseThrow(NoteFindingNotFoundException::new);
        noteFindingRepository.delete(attended);
    }
}
