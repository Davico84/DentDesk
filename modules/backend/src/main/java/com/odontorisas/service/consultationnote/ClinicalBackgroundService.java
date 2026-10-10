package com.odontorisas.service.consultationnote;

import com.odontorisas.common.BackgroundCategory;
import com.odontorisas.common.BackgroundEntryKind;
import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.persistence.entity.ClinicalBackgroundEntry;
import com.odontorisas.persistence.entity.ConsultationNote;
import com.odontorisas.persistence.entity.HistoriaClinica;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.ClinicalBackgroundEntryRepository;
import com.odontorisas.persistence.repository.ConsultationNoteRepository;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.patient.PatientNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Antecedentes nuevos como libro de asientos de alta y baja (design.md D7, ADR-0020 D6) y la vista
 * de los vigentes (design.md D8). Los asientos se escriben solo con la nota {@code ABIERTA} y
 * bloqueada; al sellar quedan protegidos por V22.
 */
@Service
@Transactional
public class ClinicalBackgroundService {

    private final ConsultationNoteRepository noteRepository;
    private final ClinicalBackgroundEntryRepository entryRepository;
    private final HistoriaClinicaRepository historiaClinicaRepository;
    private final PatientRepository patientRepository;

    public ClinicalBackgroundService(ConsultationNoteRepository noteRepository,
                                     ClinicalBackgroundEntryRepository entryRepository,
                                     HistoriaClinicaRepository historiaClinicaRepository,
                                     PatientRepository patientRepository) {
        this.noteRepository = noteRepository;
        this.entryRepository = entryRepository;
        this.historiaClinicaRepository = historiaClinicaRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Registra un asiento en la nota abierta. Dos bajas del mismo alta se serializan por el bloqueo
     * de la nota (un paciente tiene una sola abierta); si igual se cruzaran, el índice
     * {@code ux_clinical_background_entry_closes} decide y la perdedora recibe el mismo rechazo de
     * negocio que una baja secuencial, no un conflicto genérico.
     */
    public BackgroundEntryView add(Long patientId, Long noteId, BackgroundEntryData data) {
        ConsultationNoteService.lockOpenNote(noteRepository, patientId, noteId);
        ClinicalBackgroundEntry.ClinicalBackgroundEntryBuilder entry = ClinicalBackgroundEntry.builder()
            .patientId(patientId)
            .noteId(noteId)
            .kind(data.kind())
            .description(data.description());
        if (data.kind() == BackgroundEntryKind.BAJA) {
            ClinicalBackgroundEntry closed = requireActiveAlta(patientId, data.closesEntryId());
            entry.category(closed.getCategory()).closesEntryId(closed.getId());
        } else {
            entry.category(data.category());
        }
        try {
            return BackgroundEntryView.from(entryRepository.saveAndFlush(entry.build()));
        } catch (DataIntegrityViolationException alreadyClosedConcurrently) {
            throw new BackgroundEntryNotActiveException();
        }
    }

    public void remove(Long patientId, Long noteId, Long entryId) {
        ConsultationNoteService.lockOpenNote(noteRepository, patientId, noteId);
        ClinicalBackgroundEntry entry = entryRepository.findByIdAndNoteId(entryId, noteId)
            .orElseThrow(BackgroundEntryNotFoundException::new);
        entryRepository.delete(entry);
    }

    /**
     * Vigentes = la Historia sellada (texto, fechado) + altas de notas selladas sin baja de una nota
     * sellada + la anamnesis de la ficha. Lo de una nota abierta todavía no cuenta: ni sus altas ni
     * sus bajas (spec: <i>Los asientos de una nota abierta no son vigentes todavía</i>).
     */
    @Transactional(readOnly = true)
    public ActiveBackgroundView active(Long patientId) {
        Patient patient = patientRepository.findById(patientId).orElseThrow(PatientNotFoundException::new);
        List<ActiveBackgroundView.Item> items = new ArrayList<>();

        historiaClinicaRepository.findByPatientId(patientId)
            .filter(h -> h.getStatus() == HistoriaClinicaStatus.COMPLETED)
            .ifPresent(h -> addHistoria(items, h));

        Map<Long, ConsultationNote> sealed = noteRepository.findByPatientIdOrderByAttendedAtAscIdAsc(patientId).stream()
            .filter(n -> n.getStatus() == ConsultationNoteStatus.SELLADA)
            .collect(Collectors.toMap(ConsultationNote::getId, Function.identity()));
        List<ClinicalBackgroundEntry> sealedEntries = entryRepository.findByPatientIdOrderByIdAsc(patientId).stream()
            .filter(e -> sealed.containsKey(e.getNoteId()))
            .toList();
        Set<Long> closed = sealedEntries.stream()
            .filter(e -> e.getKind() == BackgroundEntryKind.BAJA)
            .map(ClinicalBackgroundEntry::getClosesEntryId)
            .collect(Collectors.toSet());
        sealedEntries.stream()
            .filter(e -> e.getKind() == BackgroundEntryKind.ALTA && !closed.contains(e.getId()))
            .forEach(e -> items.add(new ActiveBackgroundView.Item(e.getCategory(), ActiveBackgroundView.Source.NOTA,
                e.getDescription(), sealed.get(e.getNoteId()).getAttendedAt(), e.getNoteId(), e.getId())));

        addFicha(items, BackgroundCategory.ALERGIA_RAM, patient.getAllergies());
        addFicha(items, BackgroundCategory.ANTECEDENTE_PATOLOGICO, patient.getConditions());
        addFicha(items, BackgroundCategory.MEDICACION, patient.getCurrentMedication());
        return new ActiveBackgroundView(items);
    }

    /**
     * Un alta de este paciente, en una nota <b>sellada</b> y sin baja (design.md D7). La unicidad de
     * la baja la garantiza además el índice {@code ux_clinical_background_entry_closes}.
     */
    private ClinicalBackgroundEntry requireActiveAlta(Long patientId, Long entryId) {
        ClinicalBackgroundEntry alta = entryRepository.findById(entryId)
            .filter(e -> e.getPatientId().equals(patientId))
            .orElseThrow(BackgroundEntryNotActiveException::new);
        boolean inSealedNote = noteRepository.findById(alta.getNoteId())
            .map(n -> n.getStatus() == ConsultationNoteStatus.SELLADA)
            .orElse(false);
        if (alta.getKind() != BackgroundEntryKind.ALTA || !inSealedNote || entryRepository.existsByClosesEntryId(entryId)) {
            throw new BackgroundEntryNotActiveException();
        }
        return alta;
    }

    private static void addHistoria(List<ActiveBackgroundView.Item> items, HistoriaClinica h) {
        addText(items, BackgroundCategory.ALERGIA_RAM, h.getAlergiasDetalle(), h);
        addText(items, BackgroundCategory.ANTECEDENTE_PATOLOGICO, h.getAntecedentesPersonales(), h);
        addText(items, BackgroundCategory.MEDICACION, h.getMedicacionDetalle(), h);
    }

    private static void addText(List<ActiveBackgroundView.Item> items, BackgroundCategory category, String text,
                                HistoriaClinica h) {
        if (text != null && !text.isBlank()) {
            items.add(new ActiveBackgroundView.Item(category, ActiveBackgroundView.Source.HISTORIA, text,
                h.getCompletedAt(), null, null));
        }
    }

    private static void addFicha(List<ActiveBackgroundView.Item> items, BackgroundCategory category,
                                 List<String> values) {
        if (values == null) {
            return;
        }
        values.stream()
            .filter(v -> v != null && !v.isBlank())
            .forEach(v -> items.add(new ActiveBackgroundView.Item(category, ActiveBackgroundView.Source.FICHA, v,
                null, null, null)));
    }
}
