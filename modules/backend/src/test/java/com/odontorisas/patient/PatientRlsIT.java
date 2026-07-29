package com.odontorisas.patient;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.DocumentType;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.service.patient.DocumentAlreadyExistsException;
import com.odontorisas.service.patient.PatientData;
import com.odontorisas.service.patient.PatientSearch;
import com.odontorisas.service.patient.PatientService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test de aislamiento de <b>MOTOR</b> (RLS) de {@code patients} — el obligatorio (docs/testing.md).
 * Es la <b>primera prueba real sobre una entidad de negocio</b> de la garantía de
 * {@code update-multi-tenancy}: una clínica nunca ve ni escribe pacientes de otra.
 *
 * <p>Corre como el rol de aplicación <b>no-superusuario</b> (extiende
 * {@link AbstractTenantAwareIntegrationTest}) — con el superusuario del contenedor RLS ni se
 * aplicaría—. Entra por el <b>servicio/repositorio</b> con el contexto poblado <b>como lo haría el
 * filtro JWT</b>, y <b>nunca llama a {@code TenantSession.bind(...)}</b>: esa ausencia es el aserto.
 */
@SpringBootTest
class PatientRlsIT extends AbstractTenantAwareIntegrationTest {

    private static final long ORG_A = 1L;
    private static final long ORG_B = 2L;
    private static final long SOME_USER = 1L;

    @Autowired
    private PatientService patientService;

    /** Dos organizaciones reales (FK de patients) y la tabla de pacientes limpia. Como owner. */
    @BeforeEach
    void seedOrganizations() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            // CASCADE: historia_clinica/clinical_history_diagnosis referencian patients por FK
            // (add-clinical-history); sin CASCADE, TRUNCATE falla aunque estén vacías.
            s.execute("TRUNCATE patients RESTART IDENTITY CASCADE");
            s.execute("INSERT INTO organizations (id, name, slug) VALUES "
                + "(" + ORG_A + ", 'Clinica A', 'clinica-a'), "
                + "(" + ORG_B + ", 'Clinica B', 'clinica-b') "
                + "ON CONFLICT (id) DO NOTHING");
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    /** Puebla el contexto igual que el filtro JWT tras verificar el token. NO llama a bind(). */
    private void asClinic(long organizationId) {
        IdentityContext.set(SOME_USER);
        OrganizationContext.set(organizationId);
    }

    private PatientData patient(String fullName, String document) {
        DocumentType type = document == null ? null : DocumentType.DNI;
        return new PatientData(fullName, type, document, LocalDate.of(2000, 1, 1), null,
            null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private List<String> namesVisibleTo(long organizationId) {
        asClinic(organizationId);
        return patientService.search(new PatientSearch(null, true), PageRequest.of(0, 50))
            .map(s -> s.fullName()).getContent();
    }

    @Test
    void a_clinic_only_sees_its_own_patients() {
        asClinic(ORG_A);
        patientService.create(patient("Ada de A", "A1"));
        patientService.create(patient("Alan de A", "A2"));

        asClinic(ORG_B);
        patientService.create(patient("Bruno de B", "B1"));

        assertThat(namesVisibleTo(ORG_A)).containsExactlyInAnyOrder("Ada de A", "Alan de A");
        assertThat(namesVisibleTo(ORG_B)).containsExactly("Bruno de B");
    }

    @Test
    void an_operation_without_context_fails_closed() {
        // Sin IdentityContext ni OrganizationContext: el enganche no fija nada y la política
        // (sin missing_ok) hace que current_setting('app.organization_id') LANCE.
        assertThatThrownBy(() -> patientService.search(new PatientSearch(null, true), PageRequest.of(0, 50)))
            .as("una consulta de pacientes sin contexto debe FALLAR, no devolver vacío")
            .isInstanceOf(Exception.class);
    }

    @Test
    void the_same_document_is_allowed_in_another_clinic() {
        asClinic(ORG_A);
        patientService.create(patient("Persona en A", "SHARED-DOC"));

        asClinic(ORG_B);
        // La misma persona atendida en otra clínica: mismo documento, sin conflicto (ADR-0004).
        patientService.create(patient("Persona en B", "SHARED-DOC"));

        assertThat(namesVisibleTo(ORG_B)).containsExactly("Persona en B");
    }

    @Test
    void a_document_of_an_archived_patient_stays_taken() {
        asClinic(ORG_A);
        var archived = patientService.create(patient("Vuelve luego", "REUSE"));
        patientService.deactivate(archived.id());

        // El documento sigue ocupado aunque el paciente esté de baja (índice incondicional, D1).
        assertThatThrownBy(() -> patientService.create(patient("Otro con mismo doc", "REUSE")))
            .isInstanceOf(DocumentAlreadyExistsException.class);
    }
}
