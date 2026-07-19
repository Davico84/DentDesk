package com.odontorisas.patient;

import com.odontorisas.AbstractTenantAwareIntegrationTest;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Patient;
import com.odontorisas.persistence.repository.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jpa.domain.Specification;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.odontorisas.persistence.specification.PatientSpecifications.activeOnly;
import static com.odontorisas.persistence.specification.PatientSpecifications.matches;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba a nivel <b>repositorio</b> (docs/testing.md §5, hueco cerrado): {@code PatientSpecifications}
 * declara explícitamente que <b>no filtra por organización</b> — confía en que Hibernate
 * ({@code @TenantId}) y RLS lo hagan solos. Este test prueba <b>esa promesa</b>, no solo el
 * {@code LIKE}: una specification que "olvidara" el filtro de organización pasaría en verde
 * en un test que no ejercitara el aislamiento real.
 *
 * <p><b>Por qué {@code @SpringBootTest} y no {@code @DataJpaTest}</b>: se probó primero el
 * slice restringido, importando a mano {@code OrganizationTenantResolver} y
 * {@code TenantAwareTransactionManager} — y aun así el contexto de tenant nunca se fijaba
 * (fallo real: {@code unrecognized configuration parameter "app.organization_id"}, la variable
 * de sesión de Postgres ni se creaba). La maquinaria de tenencia de este proyecto es demasiado
 * profunda para que el slice la reconstruya de forma confiable: mejor un test correcto con el
 * contexto completo que un slice rápido con falsa confianza. Lo que lo hace "de repositorio" es
 * el <b>alcance</b> (solo {@code PatientRepository}, sin pasar por {@code PatientService}), no
 * la anotación.
 *
 * <p>Corre sobre el rol de aplicación no-superusuario ({@link AbstractTenantAwareIntegrationTest}):
 * con el superusuario RLS ni se aplicaría y el test no probaría nada.
 */
@SpringBootTest
class PatientSpecificationsTest extends AbstractTenantAwareIntegrationTest {

    private static final long ORG_A = 1L;
    private static final long ORG_B = 2L;

    private long someUser;

    @Autowired
    private PatientRepository patientRepository;

    @BeforeEach
    void seedOrganizationAndUser() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute("TRUNCATE patients RESTART IDENTITY");
            s.execute("INSERT INTO organizations (id, name, slug) VALUES "
                + "(" + ORG_A + ", 'Clinica A', 'clinica-a'), "
                + "(" + ORG_B + ", 'Clinica B', 'clinica-b') ON CONFLICT (id) DO NOTHING");
            // created_by/updated_by tienen FK real a users(id) (update-patients design.md D1): el
            // contenedor es singleton (compartido por toda la suite), así que un id fijo choca
            // con el IDENTITY de otra clase — se deja que la BD lo genere.
            try (ResultSet rs = s.executeQuery(
                    "INSERT INTO users (email, password_hash) VALUES "
                    + "('" + UUID.randomUUID() + "@test.local', 'x') RETURNING id")) {
                rs.next();
                someUser = rs.getLong(1);
            }
        }
    }

    @AfterEach
    void clearContext() {
        IdentityContext.clear();
        OrganizationContext.clear();
    }

    private void asClinic(long organizationId) {
        IdentityContext.set(someUser);
        OrganizationContext.set(organizationId);
    }

    private Patient patient(String fullName, String document, PatientStatus status) {
        return Patient.builder()
            .fullName(fullName)
            .document(document)
            .documentType(document == null ? null : com.odontorisas.common.DocumentType.DNI)
            .birthDate(LocalDate.of(2000, 1, 1))
            .status(status)
            .allergies(List.of())
            .conditions(List.of())
            .currentMedication(List.of())
            .build();
    }

    @Test
    void matches_never_leaks_across_organizations() {
        asClinic(ORG_A);
        patientRepository.save(patient("Ada Lovelace", "MATCH-A", PatientStatus.ACTIVE));

        asClinic(ORG_B);
        patientRepository.save(patient("Ada Wong", "MATCH-B", PatientStatus.ACTIVE));

        asClinic(ORG_A);
        Specification<Patient> spec = matches("Ada");
        List<Patient> results = patientRepository.findAll(spec);

        assertThat(results).extracting(Patient::getFullName).containsExactly("Ada Lovelace");
    }

    @Test
    void matches_finds_by_name_or_document_case_insensitively() {
        asClinic(ORG_A);
        patientRepository.save(patient("Grace Hopper", "GH-1", PatientStatus.ACTIVE));
        patientRepository.save(patient("Otro Paciente", "grace-doc", PatientStatus.ACTIVE));
        patientRepository.save(patient("Sin Relación", "XYZ", PatientStatus.ACTIVE));

        List<Patient> byName = patientRepository.findAll(matches("GRACE"));

        assertThat(byName).extracting(Patient::getFullName)
            .containsExactlyInAnyOrder("Grace Hopper", "Otro Paciente");
    }

    @Test
    void active_only_excludes_deactivated_patients() {
        asClinic(ORG_A);
        patientRepository.save(patient("Activo", "ACT-1", PatientStatus.ACTIVE));
        patientRepository.save(patient("Inactivo", "INACT-1", PatientStatus.INACTIVE));

        List<Patient> results = patientRepository.findAll(activeOnly());

        assertThat(results).extracting(Patient::getFullName).containsExactly("Activo");
    }
}
