package com.odontorisas.patient;

import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.patient.DocumentAlreadyExistsException;
import com.odontorisas.service.patient.PatientData;
import com.odontorisas.service.patient.PatientNotFoundException;
import com.odontorisas.service.patient.PatientService;
import com.odontorisas.service.patient.PatientView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lógica del servicio de pacientes con el repositorio mockeado: unicidad del documento,
 * baja/reactivación y las reglas que no dependen de la base. El aislamiento entre clínicas y la
 * unicidad a nivel de motor se prueban en los {@code *IT} (contra PostgreSQL real).
 */
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    PatientRepository patientRepository;

    @InjectMocks
    PatientService patientService;

    private PatientData data(String fullName, String document) {
        return new PatientData(fullName, null, document, LocalDate.of(1990, 1, 1), null,
            null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private void repoEchoesSave() {
        when(patientRepository.save(any(Patient.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void create_saves_an_active_patient() {
        repoEchoesSave();
        PatientView view = patientService.create(data("Ada Lovelace", "123"));

        assertThat(view.fullName()).isEqualTo("Ada Lovelace");
        assertThat(view.status()).isEqualTo(PatientStatus.ACTIVE);
    }

    @Test
    void create_rejects_a_duplicate_document_in_the_same_clinic() {
        when(patientRepository.existsByDocument("123")).thenReturn(true);

        assertThatThrownBy(() -> patientService.create(data("Ada", "123")))
            .isInstanceOf(DocumentAlreadyExistsException.class);
        verify(patientRepository, never()).save(any());
    }

    @Test
    void create_without_document_skips_the_uniqueness_check() {
        repoEchoesSave();
        patientService.create(data("Bebé Sin DNI", null));

        verify(patientRepository, never()).existsByDocument(any());
    }

    @Test
    void create_defaults_absent_anamnesis_to_empty_lists() {
        repoEchoesSave();
        PatientView view = patientService.create(data("Ada", null));

        assertThat(view.allergies()).isEmpty();
        assertThat(view.conditions()).isEmpty();
        assertThat(view.currentMedication()).isEmpty();
    }

    @Test
    void update_on_a_missing_patient_fails_not_found() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.update(99L, data("Ada", null)))
            .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void update_rejects_a_document_that_another_patient_of_the_clinic_already_has() {
        Patient existing = Patient.builder().id(1L).status(PatientStatus.ACTIVE).build();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(patientRepository.existsByDocumentAndIdNot("999", 1L)).thenReturn(true);

        assertThatThrownBy(() -> patientService.update(1L, data("Ada", "999")))
            .isInstanceOf(DocumentAlreadyExistsException.class);
        verify(patientRepository, never()).save(any());
    }

    @Test
    void deactivate_archives_then_reactivate_restores() {
        Patient p = Patient.builder().id(1L).status(PatientStatus.ACTIVE).build();
        when(patientRepository.findById(1L)).thenReturn(Optional.of(p));
        repoEchoesSave();

        assertThat(patientService.deactivate(1L).status()).isEqualTo(PatientStatus.INACTIVE);
        assertThat(patientService.reactivate(1L).status()).isEqualTo(PatientStatus.ACTIVE);
    }

    @Test
    void get_on_a_missing_patient_fails_not_found() {
        when(patientRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getById(7L))
            .isInstanceOf(PatientNotFoundException.class);
    }
}
