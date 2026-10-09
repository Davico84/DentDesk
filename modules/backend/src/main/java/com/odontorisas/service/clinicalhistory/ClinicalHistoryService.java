package com.odontorisas.service.clinicalhistory;

import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.persistence.entity.HistoriaClinica;
import com.odontorisas.persistence.repository.ClinicalHistoryDiagnosisRepository;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.patient.PatientNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Creación, guardado incremental y sellado del documento inicial de la Historia Clínica
 * (design.md D1/D2/D3/D4, ADR-0011).
 *
 * <p><b>Sin código de aislamiento</b> (docs/backend.md §8.1): el contexto de organización lo fija
 * {@code TenantAwareTransactionManager}; declarar la entidad con {@code @TenantId} basta.
 */
@Service
@Transactional
public class ClinicalHistoryService {

    private final HistoriaClinicaRepository historiaClinicaRepository;
    private final PatientRepository patientRepository;
    private final ClinicalHistoryDiagnosisRepository diagnosisRepository;
    private final Clock clock;

    public ClinicalHistoryService(HistoriaClinicaRepository historiaClinicaRepository,
                                   PatientRepository patientRepository,
                                   ClinicalHistoryDiagnosisRepository diagnosisRepository,
                                   Clock clock) {
        this.historiaClinicaRepository = historiaClinicaRepository;
        this.patientRepository = patientRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.clock = clock;
    }

    /**
     * Crea la Historia Clínica en {@code DRAFT} para un paciente de la clínica activa. Rechaza si
     * ya existe una — por pre-chequeo en la vía feliz, y por la violación de
     * {@code UNIQUE(patient_id)} si dos creaciones concurrentes se cruzan (spec: creación
     * concurrente): la unicidad la garantiza el dato, no solo este chequeo.
     */
    public ClinicalHistoryView create(Long patientId) {
        requirePatientExists(patientId);
        requireNoExistingHistoria(patientId);

        HistoriaClinica historia = HistoriaClinica.builder()
            .patientId(patientId)
            .status(HistoriaClinicaStatus.DRAFT)
            .alergiasRevisado(false)
            .medicacionRevisado(false)
            .antecedentesFamiliaresRevisado(false)
            .build();
        try {
            return ClinicalHistoryView.from(historiaClinicaRepository.save(historia));
        } catch (DataIntegrityViolationException raceLostToAnotherCreation) {
            throw new HistoriaClinicaAlreadyExistsException();
        }
    }

    @Transactional(readOnly = true)
    public ClinicalHistoryView getByPatientId(Long patientId) {
        return ClinicalHistoryView.from(findByPatientOrThrow(patientId));
    }

    /**
     * Guarda incremental en {@code DRAFT}: secciones incompletas se aceptan, no hay diálogo de
     * confirmación. No lee, copia ni modifica la anamnesis de {@code patients} (design.md D9) —
     * esta operación solo toca la fila de {@code historia_clinica}. Bloquea la Historia antes de
     * comprobar {@code DRAFT} ({@link #lockDraft}): un guardado que coincide con un sellado no la reabre.
     */
    public ClinicalHistoryView update(Long patientId, ClinicalHistoryData data) {
        HistoriaClinica historia = lockDraft(historiaClinicaRepository, patientId);
        apply(data, historia);
        return ClinicalHistoryView.from(historiaClinicaRepository.save(historia));
    }

    /**
     * Sella la Historia a {@code COMPLETED} (design.md D4): valida el checklist mínimo (rechaza
     * con {@link ChecklistIncompleteException} listando lo que falta) y la transición
     * <b>unidireccional</b> (rechaza si ya está {@code COMPLETED} — spec: transición inválida).
     *
     * <p>🔴 <b>Firma cambiada por {@code add-dental-chart}</b> (proposal.md, BREAKING): el sellado
     * de la Historia deja de ser una operación autónoma — el checklist gana el ítem {@code
     * dentalChart} (ADR-0012 D5, spec del delta de `clinical-history`: <i>Sellado a COMPLETED con
     * checklist mínimo afirmativo</i>). Este método <b>no conoce</b> el tipo {@code
     * DentalChartBlock} ni el servicio de dental-chart (design.md D4: ningún servicio de dominio
     * conoce al otro) — recibe el hecho ya resuelto como {@code boolean}. Quien orquesta ambos
     * sellados en una sola transacción es {@code CloseAdmissionService}, el único llamador
     * legítimo desde que existe (la ruta pública sigue siendo {@code
     * POST /patients/{id}/clinical-history/complete}, que ahora delega en el coordinador en vez
     * de llamar aquí directo).
     *
     * @param hasInitialDentalChart si el paciente tiene un odontograma inicial registrado
     *                              (cualquier estado) — su completitud propia (especificaciones y
     *                              observaciones revisadas) la exige el sellado del odontograma
     *                              mismo, no este checklist
     */
    public ClinicalHistoryView complete(Long patientId, boolean hasInitialDentalChart) {
        // Dos sellados concurrentes se serializan y el segundo ve COMPLETED en vez de volver a
        // sellar (design.md D4 de add-dental-chart, tarea 2.8a).
        HistoriaClinica historia = lockDraft(historiaClinicaRepository, patientId);

        long diagnosisCount = diagnosisRepository.countByHistoriaId(historia.getId());
        List<String> missing = ClinicalHistoryChecklist.missingFields(historia, diagnosisCount, hasInitialDentalChart);
        if (!missing.isEmpty()) {
            throw new ChecklistIncompleteException(missing);
        }

        historia.setStatus(HistoriaClinicaStatus.COMPLETED);
        historia.setCompletedAt(Instant.now(clock));
        return ClinicalHistoryView.from(historiaClinicaRepository.save(historia));
    }

    /**
     * Estado de habilitación por paciente (design.md D8, spec: estado consultable). {@code NONE}
     * si no hay Historia; solo {@code COMPLETED} habilita para tratamientos.
     */
    @Transactional(readOnly = true)
    public EnablementView getEnablementStatus(Long patientId) {
        return historiaClinicaRepository.findByPatientId(patientId)
            .map(h -> new EnablementView(h.getStatus().name(), h.getStatus() == HistoriaClinicaStatus.COMPLETED))
            .orElseGet(() -> new EnablementView("NONE", false));
    }

    private void requirePatientExists(Long patientId) {
        if (!patientRepository.existsById(patientId)) {
            throw new PatientNotFoundException();
        }
    }

    private void requireNoExistingHistoria(Long patientId) {
        if (historiaClinicaRepository.findByPatientId(patientId).isPresent()) {
            throw new HistoriaClinicaAlreadyExistsException();
        }
    }

    private HistoriaClinica findByPatientOrThrow(Long patientId) {
        return historiaClinicaRepository.findByPatientId(patientId)
            .orElseThrow(HistoriaClinicaNotFoundException::new);
    }

    /**
     * La puerta de toda escritura de la información inicial (`update-clinical-history` design D1):
     * bloquea la Historia ({@code SELECT ... FOR UPDATE}) y <b>después</b> exige {@code DRAFT}, para
     * que el estado leído sea el ya confirmado por cualquier sellado concurrente. Sin el bloqueo, una
     * escritura que leyó {@code DRAFT} se cuela durante el sellado — y como Hibernate guarda todas
     * las columnas (sin {@code @Version}), guardar la Historia la <b>reabriría</b>.
     *
     * <p>⚠️ <b>Regla de uso:</b> tiene que ser la <b>primera</b> carga de la Historia en la
     * transacción. Si quien llama ya la cargó antes (p. ej. una transacción más amplia que hizo
     * {@code findByPatientId}), Hibernate devuelve esa instancia del contexto de persistencia sin
     * refrescar su estado y el bloqueo deja de proteger.
     */
    static HistoriaClinica lockDraft(HistoriaClinicaRepository repository, Long patientId) {
        HistoriaClinica historia = repository.lockByPatientId(patientId)
            .orElseThrow(HistoriaClinicaNotFoundException::new);
        requireDraft(historia);
        return historia;
    }

    /** Rechaza cualquier escritura sobre información inicial si ya está sellada (design.md D1). */
    static void requireDraft(HistoriaClinica historia) {
        if (historia.getStatus() != HistoriaClinicaStatus.DRAFT) {
            throw new HistoriaClinicaSealedException();
        }
    }

    private void apply(ClinicalHistoryData d, HistoriaClinica h) {
        h.setMotivoConsulta(d.motivoConsulta());
        h.setEnfermedadActual(d.enfermedadActual());
        h.setAntecedentesPersonales(d.antecedentesPersonales());
        h.setExamenClinico(d.examenClinico());
        h.setPronostico(d.pronostico());
        h.setSignosVitales(d.signosVitales());
        h.setAlergiasRevisado(d.alergiasRevisado());
        h.setAlergiasDetalle(d.alergiasDetalle());
        h.setMedicacionRevisado(d.medicacionRevisado());
        h.setMedicacionDetalle(d.medicacionDetalle());
        h.setAntecedentesFamiliaresRevisado(d.antecedentesFamiliaresRevisado());
        h.setAntecedentesFamiliaresDetalle(d.antecedentesFamiliaresDetalle());
    }
}
