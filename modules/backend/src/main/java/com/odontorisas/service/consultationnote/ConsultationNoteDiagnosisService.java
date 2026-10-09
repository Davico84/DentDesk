package com.odontorisas.service.consultationnote;

import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.persistence.entity.ConsultationNote;
import com.odontorisas.persistence.entity.ConsultationNoteDiagnosis;
import com.odontorisas.persistence.repository.ClinicalHistoryDiagnosisRepository;
import com.odontorisas.persistence.repository.ConsultationNoteDiagnosisRepository;
import com.odontorisas.persistence.repository.ConsultationNoteRepository;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import com.odontorisas.service.clinicalhistory.Cie10CodeNotFoundException;
import com.odontorisas.service.clinicalhistory.Cie10SearchService;
import com.odontorisas.service.clinicalhistory.DiagnosisData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Diagnósticos de la nota de consulta (design.md D5): mismas reglas que los de la Historia —CIE10
 * del catálogo, tipo P/D/R, ubicación con FDI validada en el DTO— y solo con la nota {@code ABIERTA}
 * y bloqueada. Agrega la regla del <b>repetitivo</b>: el código tiene que haberse registrado antes
 * al paciente, en la Historia o en una nota sellada (NTS 139 §4.2.9).
 */
@Service
@Transactional
public class ConsultationNoteDiagnosisService {

    private final ConsultationNoteRepository noteRepository;
    private final ConsultationNoteDiagnosisRepository diagnosisRepository;
    private final HistoriaClinicaRepository historiaClinicaRepository;
    private final ClinicalHistoryDiagnosisRepository historyDiagnosisRepository;
    private final Cie10SearchService cie10SearchService;

    public ConsultationNoteDiagnosisService(ConsultationNoteRepository noteRepository,
                                            ConsultationNoteDiagnosisRepository diagnosisRepository,
                                            HistoriaClinicaRepository historiaClinicaRepository,
                                            ClinicalHistoryDiagnosisRepository historyDiagnosisRepository,
                                            Cie10SearchService cie10SearchService) {
        this.noteRepository = noteRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.historiaClinicaRepository = historiaClinicaRepository;
        this.historyDiagnosisRepository = historyDiagnosisRepository;
        this.cie10SearchService = cie10SearchService;
    }

    public NoteDiagnosisView add(Long patientId, Long noteId, DiagnosisData data) {
        ConsultationNote note = ConsultationNoteService.lockOpenNote(noteRepository, patientId, noteId);
        requireValid(patientId, data);
        ConsultationNoteDiagnosis diagnosis = ConsultationNoteDiagnosis.builder()
            .noteId(note.getId())
            .cie10Code(data.cie10Code())
            .ubicacion(data.ubicacion())
            .fdiNumber(data.fdiNumber())
            .tipoDiagnostico(data.tipoDiagnostico())
            .build();
        return NoteDiagnosisView.from(diagnosisRepository.save(diagnosis));
    }

    public NoteDiagnosisView update(Long patientId, Long noteId, Long diagnosisId, DiagnosisData data) {
        ConsultationNoteService.lockOpenNote(noteRepository, patientId, noteId);
        requireValid(patientId, data);
        ConsultationNoteDiagnosis diagnosis = diagnosisRepository.findByIdAndNoteId(diagnosisId, noteId)
            .orElseThrow(NoteDiagnosisNotFoundException::new);
        diagnosis.setCie10Code(data.cie10Code());
        diagnosis.setUbicacion(data.ubicacion());
        diagnosis.setFdiNumber(data.fdiNumber());
        diagnosis.setTipoDiagnostico(data.tipoDiagnostico());
        return NoteDiagnosisView.from(diagnosisRepository.save(diagnosis));
    }

    public void remove(Long patientId, Long noteId, Long diagnosisId) {
        ConsultationNoteService.lockOpenNote(noteRepository, patientId, noteId);
        ConsultationNoteDiagnosis diagnosis = diagnosisRepository.findByIdAndNoteId(diagnosisId, noteId)
            .orElseThrow(NoteDiagnosisNotFoundException::new);
        diagnosisRepository.delete(diagnosis);
    }

    private void requireValid(Long patientId, DiagnosisData data) {
        if (!cie10SearchService.exists(data.cie10Code())) {
            throw new Cie10CodeNotFoundException();
        }
        if (data.tipoDiagnostico() == TipoDiagnostico.REPETIDO && !registeredBefore(patientId, data.cie10Code())) {
            throw new RepeatedDiagnosisWithoutPriorException();
        }
    }

    /** En la Historia o en una nota sellada; la misma nota abierta no cuenta (design.md D5). */
    private boolean registeredBefore(Long patientId, String code) {
        boolean inHistoria = historiaClinicaRepository.findByPatientId(patientId)
            .map(h -> historyDiagnosisRepository.existsByHistoriaIdAndCie10Code(h.getId(), code))
            .orElse(false);
        return inHistoria || diagnosisRepository.existsInSealedNoteOfPatient(patientId, code);
    }
}
