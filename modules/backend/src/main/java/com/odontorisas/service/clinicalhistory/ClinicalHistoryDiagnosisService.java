package com.odontorisas.service.clinicalhistory;

import com.odontorisas.persistence.entity.ClinicalHistoryDiagnosis;
import com.odontorisas.persistence.entity.HistoriaClinica;
import com.odontorisas.persistence.repository.ClinicalHistoryDiagnosisRepository;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Diagnósticos CIE10 de la Historia Clínica (design.md D5/D11, ADR-0011). Solo se
 * agregan/modifican/eliminan mientras la Historia esté en {@code DRAFT} (design.md D1/D5), con la
 * Historia bloqueada antes de comprobarlo ({@link ClinicalHistoryService#lockDraft}, update-clinical-history):
 * un diagnóstico no puede entrar, cambiar ni salir de una Historia que se está sellando.
 */
@Service
@Transactional
public class ClinicalHistoryDiagnosisService {

    private final HistoriaClinicaRepository historiaClinicaRepository;
    private final ClinicalHistoryDiagnosisRepository diagnosisRepository;
    private final Cie10SearchService cie10SearchService;

    public ClinicalHistoryDiagnosisService(HistoriaClinicaRepository historiaClinicaRepository,
                                            ClinicalHistoryDiagnosisRepository diagnosisRepository,
                                            Cie10SearchService cie10SearchService) {
        this.historiaClinicaRepository = historiaClinicaRepository;
        this.diagnosisRepository = diagnosisRepository;
        this.cie10SearchService = cie10SearchService;
    }

    /** Agrega un diagnóstico; requiere Historia en {@code DRAFT} y código CIE10 existente. */
    public DiagnosisView add(Long patientId, DiagnosisData data) {
        HistoriaClinica historia = ClinicalHistoryService.lockDraft(historiaClinicaRepository, patientId);
        requireCie10CodeExists(data.cie10Code());

        ClinicalHistoryDiagnosis diagnosis = ClinicalHistoryDiagnosis.builder()
            .historiaId(historia.getId())
            .cie10Code(data.cie10Code())
            .ubicacion(data.ubicacion())
            .fdiNumber(data.fdiNumber())
            .tipoDiagnostico(data.tipoDiagnostico())
            .build();
        return DiagnosisView.from(diagnosisRepository.save(diagnosis));
    }

    /** Reemplaza los datos de un diagnóstico existente; requiere Historia en {@code DRAFT}. */
    public DiagnosisView update(Long patientId, Long diagnosisId, DiagnosisData data) {
        HistoriaClinica historia = ClinicalHistoryService.lockDraft(historiaClinicaRepository, patientId);
        requireCie10CodeExists(data.cie10Code());

        ClinicalHistoryDiagnosis diagnosis = findDiagnosisOrThrow(historia.getId(), diagnosisId);
        diagnosis.setCie10Code(data.cie10Code());
        diagnosis.setUbicacion(data.ubicacion());
        diagnosis.setFdiNumber(data.fdiNumber());
        diagnosis.setTipoDiagnostico(data.tipoDiagnostico());
        return DiagnosisView.from(diagnosisRepository.save(diagnosis));
    }

    /** Elimina un diagnóstico; requiere Historia en {@code DRAFT}. */
    public void remove(Long patientId, Long diagnosisId) {
        HistoriaClinica historia = ClinicalHistoryService.lockDraft(historiaClinicaRepository, patientId);
        ClinicalHistoryDiagnosis diagnosis = findDiagnosisOrThrow(historia.getId(), diagnosisId);
        diagnosisRepository.delete(diagnosis);
    }

    @Transactional(readOnly = true)
    public List<DiagnosisView> listByPatient(Long patientId) {
        HistoriaClinica historia = findHistoriaOrThrow(patientId);
        return diagnosisRepository.findByHistoriaId(historia.getId()).stream()
            .map(DiagnosisView::from)
            .toList();
    }

    private void requireCie10CodeExists(String code) {
        if (!cie10SearchService.exists(code)) {
            throw new Cie10CodeNotFoundException();
        }
    }

    private HistoriaClinica findHistoriaOrThrow(Long patientId) {
        return historiaClinicaRepository.findByPatientId(patientId)
            .orElseThrow(HistoriaClinicaNotFoundException::new);
    }

    private ClinicalHistoryDiagnosis findDiagnosisOrThrow(Long historiaId, Long diagnosisId) {
        ClinicalHistoryDiagnosis diagnosis = diagnosisRepository.findById(diagnosisId)
            .orElseThrow(DiagnosisNotFoundException::new);
        if (!diagnosis.getHistoriaId().equals(historiaId)) {
            throw new DiagnosisNotFoundException();
        }
        return diagnosis;
    }
}
