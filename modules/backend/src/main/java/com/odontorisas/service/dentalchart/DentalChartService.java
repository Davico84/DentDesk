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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
    private final Clock clock;

    public DentalChartService(DentalChartBlockRepository blockRepository, PatientRepository patientRepository,
                               Clock clock) {
        this.blockRepository = blockRepository;
        this.patientRepository = patientRepository;
        this.clock = clock;
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
        DentalChartBlock block = lockOpenBlock(blockRepository, patientId);
        block.setSpecifications(text);
        block.setSpecificationsReviewed(true);
        return DentalChartBlockView.from(blockRepository.save(block));
    }

    /**
     * Registra las observaciones (5.15): lo que la nomenclatura del odontograma no contempla. No
     * sustituye a las especificaciones ni viceversa — son dos campos distintos (ADR-0017 D6).
     */
    public DentalChartBlockView setObservations(Long patientId, String text) {
        DentalChartBlock block = lockOpenBlock(blockRepository, patientId);
        block.setObservations(text);
        block.setObservationsReviewed(true);
        return DentalChartBlockView.from(blockRepository.save(block));
    }

    /**
     * {@code true} si el paciente tiene un odontograma inicial, en <b>cualquier</b> estado
     * (abierto o sellado). Es el hecho que {@code CloseAdmissionService} le pasa a {@code
     * ClinicalHistoryService.complete(...)} sin que ese servicio conozca este tipo (design.md D4)
     * — el ítem {@code dentalChart} del checklist de la Historia solo pregunta "¿existe?"; que su
     * contenido esté completo lo exige {@link #sealInitial}, no esta consulta.
     */
    @Transactional(readOnly = true)
    public boolean existsForPatient(Long patientId) {
        // Sin cargar el bloque: el coordinador llama a esto en la misma transacción que
        // sealInitial(), y una copia ya cargada haría que el bloqueo de sealInitial devolviera
        // un estado viejo (ver DentalChartBlockRepository#lockInitialByPatientId).
        return blockRepository.existsByPatientId(patientId);
    }

    /**
     * Sella el odontograma inicial a {@code SEALED} (ADR-0012 D3, ADR-0017 D6, spec: <i>Odontograma
     * sellado e inmutable</i>): valida su <b>propio</b> checklist — especificaciones Y
     * observaciones registradas, por separado (dos campos distintos, ninguno cubre al otro) — y
     * la transición unidireccional (rechaza si ya está {@code SEALED}). Permite sellar sin
     * hallazgos: una boca sana es un registro clínico válido, no exige un mínimo.
     *
     * <p>🔴 <b>Público a propósito, no package-private.</b> El coordinador (
     * {@code service.admission.CloseAdmissionService}) vive en otro paquete y es quien debe
     * invocarlo — design.md D4 lo describía como package-private antes de saber que el coordinador
     * cruzaría paquetes; la visibilidad real la impone Java, así que "solo lo llama el
     * coordinador" lo sostiene una regla de ArchUnit ({@code AdmissionSealingArchitectureTest},
     * tarea 2.8e), no el compilador.
     *
     * <p>Bloquea el bloque antes de validar ({@link #lockOpenBlock}): dentro del cierre de
     * admisión es el tercer eslabón del orden paciente → historia → odontograma (design.md D4).
     */
    public DentalChartBlockView sealInitial(Long patientId) {
        DentalChartBlock block = lockOpenBlock(blockRepository, patientId);

        List<String> missing = DentalChartChecklist.missingFields(block);
        if (!missing.isEmpty()) {
            throw new DentalChartChecklistIncompleteException(missing);
        }

        block.setStatus(DentalChartBlockStatus.SEALED);
        block.setSealedAt(Instant.now(clock));
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

    /**
     * Bloquea el odontograma ({@code SELECT ... FOR UPDATE}) y exige que siga {@code OPEN}: la
     * puerta de toda escritura del bloque o de sus hallazgos (design.md D9). El orden importa —
     * primero el bloqueo, después la comprobación — para que el estado leído sea el confirmado por
     * cualquier sellado concurrente, no uno anterior.
     */
    static DentalChartBlock lockOpenBlock(DentalChartBlockRepository blockRepository, Long patientId) {
        DentalChartBlock block = blockRepository.lockInitialByPatientId(patientId)
            .orElseThrow(DentalChartBlockNotFoundException::new);
        requireOpen(block);
        return block;
    }

    /** Rechaza cualquier escritura si el odontograma ya está sellado (ADR-0012 D3). */
    static void requireOpen(DentalChartBlock block) {
        if (block.getStatus() != DentalChartBlockStatus.OPEN) {
            throw new DentalChartBlockSealedException();
        }
    }
}
