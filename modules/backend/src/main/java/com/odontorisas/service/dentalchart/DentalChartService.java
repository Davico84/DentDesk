package com.odontorisas.service.dentalchart;

import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.common.DentalChartBlockType;
import com.odontorisas.persistence.entity.DentalChartBlock;
import com.odontorisas.persistence.repository.DentalChartBlockRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.patient.PatientNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Creación del odontograma inicial y sus dos campos de texto (ADR-0012, ADR-0017 D3/D6). Mismo
 * patrón que {@link com.odontorisas.service.clinicalhistory.ClinicalHistoryService}: sin código
 * de aislamiento — {@code TenantAwareTransactionManager} fija el contexto, {@code @TenantId}
 * basta.
 */
@Service
@Transactional
public class DentalChartService {

    private final DentalChartBlockRepository blockRepository;
    private final PatientRepository patientRepository;

    public DentalChartService(DentalChartBlockRepository blockRepository, PatientRepository patientRepository) {
        this.blockRepository = blockRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Crea el odontograma inicial en {@code OPEN} para un paciente de la clínica activa. Rechaza
     * si ya existe — por pre-chequeo en la vía feliz, y por la violación de los índices únicos
     * parciales si dos creaciones concurrentes se cruzan (design.md D3, spec: creación
     * concurrente): la unicidad la garantiza el dato, no solo este chequeo.
     *
     * @param examinedOn la fecha del evento clínico (NTS 188 5.10/5.11) — no la de creación de la fila
     */
    public DentalChartBlockView createInitial(Long patientId, LocalDate examinedOn) {
        requirePatientExists(patientId);
        requireNoExistingBlock(patientId);

        DentalChartBlock block = DentalChartBlock.builder()
            .patientId(patientId)
            .type(DentalChartBlockType.INITIAL)
            .status(DentalChartBlockStatus.OPEN)
            .examinedOn(examinedOn)
            .build();
        try {
            return DentalChartBlockView.from(blockRepository.save(block));
        } catch (DataIntegrityViolationException raceLostToAnotherCreation) {
            throw new DentalChartBlockAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public DentalChartBlockView getByPatientId(Long patientId) {
        return DentalChartBlockView.from(findByPatientOrThrow(patientId));
    }

    /**
     * Registra las especificaciones (5.14): lo que sí está en el catálogo pero no cabe dibujado.
     * {@code text == null} es una afirmación válida ("revisado, nada que anotar"), no un olvido —
     * en ambos casos queda {@code specificationsReviewed = true} (design.md D5).
     */
    public DentalChartBlockView setSpecifications(Long patientId, String text) {
        DentalChartBlock block = findByPatientOrThrow(patientId);
        requireOpen(block);
        block.setSpecifications(text);
        block.setSpecificationsReviewed(true);
        return DentalChartBlockView.from(blockRepository.save(block));
    }

    /**
     * Registra las observaciones (5.15): lo que la nomenclatura del odontograma no contempla. No
     * sustituye a las especificaciones ni viceversa — son dos campos distintos (ADR-0017 D6).
     */
    public DentalChartBlockView setObservations(Long patientId, String text) {
        DentalChartBlock block = findByPatientOrThrow(patientId);
        requireOpen(block);
        block.setObservations(text);
        block.setObservationsReviewed(true);
        return DentalChartBlockView.from(blockRepository.save(block));
    }

    private void requirePatientExists(Long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new PatientNotFoundException();
        }
    }

    private void requireNoExistingBlock(Long patientId) {
        if (blockRepository.findByPatientIdAndStatus(patientId, DentalChartBlockStatus.OPEN).isPresent()) {
            throw new DentalChartBlockAlreadyExistsException();
        }
    }

    DentalChartBlock findByPatientOrThrow(Long patientId) {
        return blockRepository.findByPatientIdAndStatus(patientId, DentalChartBlockStatus.OPEN)
            .or(() -> blockRepository.findByPatientIdAndStatus(patientId, DentalChartBlockStatus.SEALED))
            .orElseThrow(DentalChartBlockNotFoundException::new);
    }

    /** Rechaza cualquier escritura si el odontograma ya está sellado (ADR-0012 D3). */
    static void requireOpen(DentalChartBlock block) {
        if (block.getStatus() != DentalChartBlockStatus.OPEN) {
            throw new DentalChartBlockSealedException();
        }
    }
}
