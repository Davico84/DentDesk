package com.odontorisas.service.admission;

import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryView;
import com.odontorisas.service.dentalchart.DentalChartBlockView;
import com.odontorisas.service.dentalchart.DentalChartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinador del cierre de admisión (design.md D4, ADR-0012 D5, `add-dental-chart`): sella la
 * Historia Clínica y el odontograma inicial en <b>una sola transacción</b>. Ninguno de los dos
 * servicios de dominio conoce al otro — {@code ClinicalHistoryService} y {@code DentalChartService}
 * no se importan entre sí; este coordinador depende de ambos y es el único punto que sabe que el
 * cierre de admisión involucra dos documentos.
 *
 * <p><b>Orden de bloqueo, único y obligatorio</b> (design.md D4): paciente → historia →
 * odontograma, cada uno con {@code SELECT ... FOR UPDATE}. Si dos rutas bloquearan los mismos
 * agregados en orden distinto, dos cierres concurrentes se deadlockearían — lo verifica con hilos
 * reales {@code CloseAdmissionServiceIT} (tarea 2.8a).
 *
 * <p><b>Pasos y atomicidad:</b>
 * <ol>
 *   <li>Bloquea al paciente.</li>
 *   <li>Pregunta si existe un odontograma inicial ({@code dentalChartService.existsForPatient}) —
 *       un {@code boolean} plano, sin exponer el tipo {@code DentalChartBlock} a clinical-history.</li>
 *   <li>Sella la Historia ({@code clinicalHistoryService.complete}, que la bloquea), pasándole ese
 *       {@code boolean} — si no hay odontograma, rechaza aquí con {@code missingFields}
 *       conteniendo {@code "dentalChart"}, sin tocar el odontograma.</li>
 *   <li>Sella el odontograma ({@code dentalChartService.sealInitial}, que lo bloquea) — valida su
 *       <b>propio</b> checklist (especificaciones y observaciones revisadas). Si falla acá,
 *       {@code @Transactional} revierte <b>también</b> el sellado de la Historia del paso 3 (spec:
 *       <i>Un fallo al sellar el odontograma deja la Historia en DRAFT</i>) — verificado con
 *       rollback real en {@code CloseAdmissionServiceIT}, no asumido por el orden de las llamadas.</li>
 * </ol>
 */
@Service
public class CloseAdmissionService {

    private final PatientRepository patientRepository;
    private final ClinicalHistoryService clinicalHistoryService;
    private final DentalChartService dentalChartService;

    public CloseAdmissionService(PatientRepository patientRepository,
                                  ClinicalHistoryService clinicalHistoryService,
                                  DentalChartService dentalChartService) {
        this.patientRepository = patientRepository;
        this.clinicalHistoryService = clinicalHistoryService;
        this.dentalChartService = dentalChartService;
    }

    @Transactional
    public AdmissionClosedResult close(Long patientId) {
        // Si el paciente no existe no se rechaza acá: el paso de la Historia ya responde como antes.
        patientRepository.lockById(patientId);
        boolean hasInitialDentalChart = dentalChartService.existsForPatient(patientId);
        ClinicalHistoryView clinicalHistory = clinicalHistoryService.complete(patientId, hasInitialDentalChart);
        DentalChartBlockView dentalChart = dentalChartService.sealInitial(patientId);
        return new AdmissionClosedResult(clinicalHistory, dentalChart);
    }
}
