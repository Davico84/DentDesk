package com.odontorisas.consultationnote;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.ClinicalSignerFixtures;
import com.odontorisas.common.DiagnosisLocation;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.PatientRepository;
import com.odontorisas.service.admission.CloseAdmissionService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryData;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryDiagnosisService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryService;
import com.odontorisas.service.clinicalhistory.DiagnosisData;
import com.odontorisas.service.consultationnote.ConsultationNoteData;
import com.odontorisas.service.consultationnote.ConsultationNoteDiagnosisService;
import com.odontorisas.service.consultationnote.ConsultationNoteService;
import com.odontorisas.service.dentalchart.DentalChartFindingData;
import com.odontorisas.service.dentalchart.DentalChartFindingService;
import com.odontorisas.service.dentalchart.DentalChartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Siembra común de los IT de la nota de consulta: una clínica, dos usuarios (el segundo para la
 * regla de autoría de la corrección) y pacientes con la Historia ya sellada por la ruta real
 * ({@link CloseAdmissionService}), que es la precondición de toda nota (design.md D1).
 */
abstract class AbstractConsultationNoteIT extends AbstractTenantAwareIntegrationTest {

    protected long org;
    protected long someUser;
    protected long otherUser;

    @Autowired
    protected PatientRepository patientRepository;
    @Autowired
    protected ClinicalHistoryService clinicalHistoryService;
    @Autowired
    protected ClinicalHistoryDiagnosisService historyDiagnosisService;
    @Autowired
    protected DentalChartService dentalChartService;
    @Autowired
    protected DentalChartFindingService dentalChartFindingService;
    @Autowired
    protected CloseAdmissionService closeAdmissionService;
    @Autowired
    protected ConsultationNoteService noteService;
    @Autowired
    protected ConsultationNoteDiagnosisService noteDiagnosisService;

    /** Alergias "Penicilina" en la Historia: la usa la vista de vigentes. */
    protected static final ClinicalHistoryData FULL_HC = new ClinicalHistoryData(
        "Dolor de muela", "Desde hace 3 días", "Hipertensión", "Sin hallazgos anormales",
        "Favorable", null, true, "Penicilina", true, null, true, null);

    /** Diagnosticado en la Historia de cada paciente sembrado. */
    protected static final String HC_CODE = "A00";

    protected static DiagnosisData diagnosis(String code, TipoDiagnostico tipo) {
        return new DiagnosisData(code, DiagnosisLocation.GENERAL, null, tipo);
    }

    @BeforeEach
    void seedClinic() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE consultation_note_finding, consultation_note_correction, clinical_background_entry, consultation_note_diagnosis, "
                + "consultation_note, dental_chart_finding_tooth, dental_chart_finding, dental_chart_block, "
                + "clinical_history_diagnosis, historia_clinica, patients RESTART IDENTITY CASCADE");
            String suffix = UUID.randomUUID().toString();
            org = id(s, "INSERT INTO organizations (name, slug) VALUES ('Clinica Notas', 'cn-" + suffix
                + "') RETURNING id");
            someUser = id(s, "INSERT INTO users (email, password_hash) VALUES ('cn1-" + suffix
                + "@test.local', 'x') RETURNING id");
            otherUser = id(s, "INSERT INTO users (email, password_hash) VALUES ('cn2-" + suffix
                + "@test.local', 'x') RETURNING id");
            // Firmar exige capacidad clínica (update-staff-professional-credential): membresía y credencial vigentes.
            ClinicalSignerFixtures.enableSigner(owner, someUser, org);
            ClinicalSignerFixtures.enableSigner(owner, otherUser, org);
        }
        asUser(someUser);
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    protected void asUser(long userId) {
        IdentityContext.set(userId);
        OrganizationContext.set(org);
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (var rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    protected Long aPatient() {
        return patientRepository.save(Patient.builder()
            .fullName("Paciente de control")
            .birthDate(LocalDate.of(1985, 5, 5))
            .status(PatientStatus.ACTIVE)
            .allergies(List.of("Látex"))
            .conditions(List.of())
            .currentMedication(List.of())
            .build()).getId();
    }

    /** Paciente con la Historia Clínica {@code COMPLETED} (diagnóstico {@link #HC_CODE}) y odontograma inicial sellados. */
    protected Long aPatientWithSealedHistory() {
        return aPatientWithSealedFindings().patientId();
    }

    /** Un paciente sembrado y los ids de los hallazgos de su odontograma inicial, en el orden dado. */
    protected record PatientWithFindings(Long patientId, List<Long> findingIds) {
    }

    /** Como {@link #aPatientWithSealedHistory()}, con estos hallazgos en el odontograma inicial (sellado con la admisión). */
    protected PatientWithFindings aPatientWithSealedFindings(DentalChartFindingData... findings) {
        Long patientId = aPatient();
        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId, FULL_HC);
        historyDiagnosisService.add(patientId, diagnosis(HC_CODE, TipoDiagnostico.DEFINITIVO));
        dentalChartService.createInitial(patientId, LocalDate.now());
        List<Long> ids = Arrays.stream(findings)
            .map(f -> dentalChartFindingService.add(patientId, f).finding().id())
            .toList();
        dentalChartService.setSpecifications(patientId, "Sin especificaciones adicionales");
        dentalChartService.setObservations(patientId, null);
        closeAdmissionService.close(patientId);
        return new PatientWithFindings(patientId, ids);
    }

    protected static Instant hoursAgo(long hours) {
        return Instant.now().minus(hours, ChronoUnit.HOURS);
    }

    protected static ConsultationNoteData complete(Instant attendedAt) {
        return new ConsultationNoteData(attendedAt, "Control", null, null, null, null,
            "Encía sana", "Profilaxis", null, null, null);
    }

    /** Abre, completa (motivo, examen, tratamiento, un diagnóstico) y sella una nota. Devuelve su id. */
    protected Long aSealedNote(Long patientId, Instant attendedAt, String code) {
        Long noteId = noteService.open(patientId, attendedAt).id();
        noteService.update(patientId, noteId, complete(attendedAt));
        noteDiagnosisService.add(patientId, noteId, diagnosis(code, TipoDiagnostico.DEFINITIVO));
        noteService.seal(patientId, noteId);
        return noteId;
    }
}
