package com.odontorisas.service.clinicalhistory;

import com.odontorisas.persistence.entity.HistoriaClinica;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El checklist mínimo de sellado (design.md D4) como función pura — sin Spring, sin base de
 * datos. Comprueba cada ítem del checklist y el patrón "campo vacío ≠ revisado" (design.md D3).
 */
class ClinicalHistoryChecklistTest {

    private HistoriaClinica.HistoriaClinicaBuilder complete() {
        return HistoriaClinica.builder()
            .motivoConsulta("Dolor de muela")
            .antecedentesPersonales("Ninguno relevante")
            .alergiasRevisado(true)
            .medicacionRevisado(true)
            .antecedentesFamiliaresRevisado(true)
            .examenClinico("Sin hallazgos")
            .pronostico("Favorable");
    }

    @Test
    void nothing_is_missing_when_the_checklist_is_fully_satisfied() {
        HistoriaClinica h = complete().build();

        assertThat(ClinicalHistoryChecklist.missingFields(h, 1)).isEmpty();
    }

    @Test
    void reports_a_blank_motivo_consulta() {
        HistoriaClinica h = complete().motivoConsulta("  ").build();

        assertThat(ClinicalHistoryChecklist.missingFields(h, 1)).contains("motivoConsulta");
    }

    @Test
    void reports_missing_antecedentes_personales() {
        HistoriaClinica h = complete().antecedentesPersonales(null).build();

        assertThat(ClinicalHistoryChecklist.missingFields(h, 1)).contains("antecedentesPersonales");
    }

    @Test
    void an_unset_alergias_revisado_flag_is_reported_even_with_no_diagnoses_missing() {
        HistoriaClinica h = complete().alergiasRevisado(false).build();

        assertThat(ClinicalHistoryChecklist.missingFields(h, 1)).containsExactly("alergiasRevisado");
    }

    @Test
    void an_unset_medicacion_revisado_flag_is_reported() {
        HistoriaClinica h = complete().medicacionRevisado(false).build();

        assertThat(ClinicalHistoryChecklist.missingFields(h, 1)).containsExactly("medicacionRevisado");
    }

    @Test
    void an_unset_antecedentes_familiares_revisado_flag_is_reported() {
        HistoriaClinica h = complete().antecedentesFamiliaresRevisado(false).build();

        assertThat(ClinicalHistoryChecklist.missingFields(h, 1)).containsExactly("antecedentesFamiliaresRevisado");
    }

    @Test
    void reports_missing_examen_clinico() {
        HistoriaClinica h = complete().examenClinico(null).build();

        assertThat(ClinicalHistoryChecklist.missingFields(h, 1)).contains("examenClinico");
    }

    @Test
    void reports_missing_pronostico() {
        HistoriaClinica h = complete().pronostico(null).build();

        assertThat(ClinicalHistoryChecklist.missingFields(h, 1)).contains("pronostico");
    }

    @Test
    void reports_missing_diagnoses_when_there_are_none() {
        HistoriaClinica h = complete().build();

        assertThat(ClinicalHistoryChecklist.missingFields(h, 0)).containsExactly("diagnosticos");
    }

    @Test
    void reports_every_missing_item_at_once() {
        HistoriaClinica h = HistoriaClinica.builder().build();

        List<String> missing = ClinicalHistoryChecklist.missingFields(h, 0);

        assertThat(missing).containsExactlyInAnyOrder(
            "motivoConsulta", "antecedentesPersonales", "alergiasRevisado", "medicacionRevisado",
            "antecedentesFamiliaresRevisado", "examenClinico", "diagnosticos", "pronostico");
    }
}
