package com.odontorisas.service.patient;

import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.HistoriaClinicaRepository;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.persistence.specification.PatientSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestión de la ficha del paciente: alta, consulta, edición, baja/reactivación y listado.
 *
 * <p><b>Sin código de aislamiento.</b> Toda consulta queda restringida a la organización del
 * contexto por Hibernate ({@code @TenantId}) + Row-Level Security; el contexto lo fija
 * {@code TenantAwareTransactionManager} en cada transacción. Este servicio <b>no toca
 * {@code TenantSession}</b> (lo impediría ArchUnit) y el {@code organizationId} <b>jamás</b>
 * viaja en la petición.
 *
 * <p>{@code @Transactional} a nivel de clase: las variables de RLS son <em>transaction-local</em>,
 * así que fuera de una transacción con contexto el motor bloquea.
 */
@Service
@Transactional
public class PatientService {

    private final PatientRepository patientRepository;
    private final HistoriaClinicaRepository historiaClinicaRepository;

    public PatientService(PatientRepository patientRepository, HistoriaClinicaRepository historiaClinicaRepository) {
        this.patientRepository = patientRepository;
        this.historiaClinicaRepository = historiaClinicaRepository;
    }

    /** Alta en la clínica activa. El documento (si viene) no puede repetirse en la clínica. */
    public PatientView create(PatientData data) {
        requireDocumentFree(data.document(), null);
        Patient patient = Patient.builder()
            .status(PatientStatus.ACTIVE)
            .build();
        apply(data, patient);
        return toView(patientRepository.save(patient));
    }

    @Transactional(readOnly = true)
    public PatientView getById(Long id) {
        return toView(find(id));
    }

    /**
     * Edición de la ficha. El documento no puede colisionar con el de otro paciente de la clínica, y
     * la anamnesis no cambia si el paciente ya tiene Historia Clínica (`update-clinical-history`
     * design.md D10): enviarla sin cambios es válido, porque el formulario manda la ficha completa.
     */
    public PatientView update(Long id, PatientData data) {
        Patient patient = find(id);
        requireDocumentFree(data.document(), id);
        if (anamnesisChanged(data, patient) && historiaClinicaRepository.existsByPatientId(id)) {
            throw new AnamnesisFrozenException();
        }
        apply(data, patient);
        return toView(patientRepository.save(patient));
    }

    /** Baja lógica: archiva, no borra (design.md D4). */
    public PatientView deactivate(Long id) {
        Patient patient = find(id);
        patient.setStatus(PatientStatus.INACTIVE);
        return toView(patientRepository.save(patient));
    }

    /** Reactivación: vuelve a ACTIVE (la salida al 409 del documento ocupado). */
    public PatientView reactivate(Long id) {
        Patient patient = find(id);
        patient.setStatus(PatientStatus.ACTIVE);
        return toView(patientRepository.save(patient));
    }

    @Transactional(readOnly = true)
    public Page<PatientSummary> search(PatientSearch search, Pageable pageable) {
        Specification<Patient> spec = PatientSpecifications.matches(search.query());
        if (!search.includeInactive()) {
            spec = spec == null ? PatientSpecifications.activeOnly()
                : spec.and(PatientSpecifications.activeOnly());
        }
        return patientRepository.findAll(spec, pageable).map(PatientSummary::from);
    }

    private Patient find(Long id) {
        return patientRepository.findById(id).orElseThrow(PatientNotFoundException::new);
    }

    /**
     * El documento es opcional; cuando viene, debe estar libre en la clínica (incondicional, D1).
     * Si colisiona, la excepción lleva el {@code id} de la ficha existente para que el cliente pueda
     * ofrecer abrirla (update-patients D3/D4) — por eso {@code findBy...}, no {@code existsBy...}.
     */
    private void requireDocumentFree(String document, Long selfId) {
        if (document == null || document.isBlank()) {
            return;
        }
        (selfId == null
            ? patientRepository.findByDocument(document)
            : patientRepository.findByDocumentAndIdNot(document, selfId))
            .ifPresent(existing -> {
                throw new DocumentAlreadyExistsException(existing.getId());
            });
    }

    /** Construye la vista con el aviso de teléfono compartido (update-patients D1) y el congelamiento de la anamnesis. */
    private PatientView toView(Patient patient) {
        return PatientView.from(patient, phoneSharedWithOthers(patient.getPhone(), patient.getId()),
            patient.getId() != null && historiaClinicaRepository.existsByPatientId(patient.getId()));
    }

    private static boolean anamnesisChanged(PatientData d, Patient p) {
        return !orEmpty(d.allergies()).equals(orEmpty(p.getAllergies()))
            || !orEmpty(d.conditions()).equals(orEmpty(p.getConditions()))
            || !orEmpty(d.currentMedication()).equals(orEmpty(p.getCurrentMedication()));
    }

    /** ¿El teléfono ya está en otros pacientes de la clínica? Excluye al propio paciente (D1). */
    private boolean phoneSharedWithOthers(String phone, Long selfId) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return patientRepository.countByPhoneAndIdNot(phone, selfId) > 0;
    }

    private void apply(PatientData d, Patient p) {
        p.setFullName(d.fullName());
        p.setDocumentType(d.documentType());
        p.setDocument(d.document());
        p.setBirthDate(d.birthDate());
        p.setSex(d.sex());
        p.setPhone(d.phone());
        p.setEmail(d.email());
        p.setAddress(d.address());
        p.setGuardianName(d.guardianName());
        p.setGuardianRelationship(d.guardianRelationship());
        p.setGuardianDocument(d.guardianDocument());
        p.setGuardianPhone(d.guardianPhone());
        p.setEmergencyContactName(d.emergencyContactName());
        p.setEmergencyContactPhone(d.emergencyContactPhone());
        p.setEmergencyContactRelationship(d.emergencyContactRelationship());
        p.setAllergies(orEmpty(d.allergies()));
        p.setConditions(orEmpty(d.conditions()));
        p.setCurrentMedication(orEmpty(d.currentMedication()));
    }

    /** La anamnesis vacía es una lista vacía, no ausente (la columna es NOT NULL DEFAULT '[]'). */
    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }
}
