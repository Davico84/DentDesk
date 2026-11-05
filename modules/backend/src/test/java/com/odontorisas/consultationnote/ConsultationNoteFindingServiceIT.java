package com.odontorisas.consultationnote;

import com.odontorisas.ClinicalSignerFixtures;
import com.odontorisas.common.DentalArch;
import com.odontorisas.common.FindingType;
import com.odontorisas.common.LocationKind;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.common.ToothSurface;
import com.odontorisas.service.consultationnote.ConsultationNoteFindingService;
import com.odontorisas.service.consultationnote.ConsultationNoteNotEmptyException;
import com.odontorisas.service.consultationnote.ConsultationNoteSealedException;
import com.odontorisas.service.consultationnote.ConsultationNoteView;
import com.odontorisas.service.consultationnote.NoteFindingAlreadyAttendedException;
import com.odontorisas.service.consultationnote.NoteFindingNotFoundException;
import com.odontorisas.service.consultationnote.NoteFindingView;
import com.odontorisas.service.dentalchart.DentalChartFindingData;
import com.odontorisas.service.dentalchart.FindingLocationData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scenarios de <i>Hallazgos del odontograma atendidos en la nota</i> y del descarte modificado de
 * <i>Nota abierta editable y descartable solo vacía</i> (update-consultation-note-findings design.md
 * D1/D7). La concurrencia con el sellado está en {@code ConsultationNoteConcurrencyIT}.
 */
@SpringBootTest
class ConsultationNoteFindingServiceIT extends AbstractConsultationNoteIT {

    private static final String CARIES_16 = "Pieza 16, cara oclusal: Lesión de caries — Mancha Blanca";

    @Autowired
    private ConsultationNoteFindingService findingService;

    private static DentalChartFindingData caries(int tooth) {
        return new DentalChartFindingData(FindingType.LESION_CARIES, "MB", null, null,
            new FindingLocationData(LocationKind.SURFACE, tooth, null, ToothSurface.OCLUSAL, null, null));
    }

    private static DentalChartFindingData edentulous() {
        return new DentalChartFindingData(FindingType.EDENTULO_TOTAL, "", null, null,
            new FindingLocationData(LocationKind.ARCH, null, null, null, DentalArch.SUPERIOR, null));
    }

    // --- Requirement: Hallazgos del odontograma atendidos en la nota ---

    @Test
    void attending_a_finding_records_it_in_the_note_with_its_description_and_author() {
        PatientWithFindings p = aPatientWithSealedFindings(caries(16));
        Long noteId = noteService.open(p.patientId(), hoursAgo(1)).id();

        NoteFindingView attended = findingService.attend(p.patientId(), noteId, p.findingIds().get(0), "  " + CARIES_16 + " ");

        assertThat(attended.description()).isEqualTo(CARIES_16);
        assertThat(attended.createdBy()).isEqualTo(someUser);
        assertThat(attended.createdAt()).isNotNull();
        assertThat(noteService.get(p.patientId(), noteId).attendedFindings())
            .extracting(NoteFindingView::findingId).containsExactly(p.findingIds().get(0));
    }

    @Test
    void a_finding_attended_in_a_sealed_note_can_be_attended_again_in_the_open_one() {
        PatientWithFindings p = aPatientWithSealedFindings(caries(16));
        Long finding = p.findingIds().get(0);
        Long sealedNoteId = noteService.open(p.patientId(), hoursAgo(5)).id();
        findingService.attend(p.patientId(), sealedNoteId, finding, CARIES_16);
        noteService.update(p.patientId(), sealedNoteId, complete(hoursAgo(5)));
        noteDiagnosisService.add(p.patientId(), sealedNoteId, diagnosis("K02", TipoDiagnostico.DEFINITIVO));
        noteService.seal(p.patientId(), sealedNoteId);
        Long openNoteId = noteService.open(p.patientId(), hoursAgo(1)).id();

        findingService.attend(p.patientId(), openNoteId, finding, CARIES_16);

        // La lista de notas (carga en bloque) trae los hallazgos de cada una: de ahí sale "Atendido el …".
        assertThat(noteService.list(p.patientId()))
            .allSatisfy(n -> assertThat(n.attendedFindings()).extracting(NoteFindingView::findingId).containsExactly(finding));
    }

    @Test
    void removing_an_attended_finding_while_the_note_is_open_drops_it() {
        PatientWithFindings p = aPatientWithSealedFindings(caries(16));
        Long noteId = noteService.open(p.patientId(), hoursAgo(1)).id();
        NoteFindingView attended = findingService.attend(p.patientId(), noteId, p.findingIds().get(0), CARIES_16);

        findingService.remove(p.patientId(), noteId, attended.id());

        assertThat(noteService.get(p.patientId(), noteId).attendedFindings()).isEmpty();
    }

    @Test
    void attending_the_same_finding_twice_in_a_note_is_rejected_and_keeps_the_first() {
        PatientWithFindings p = aPatientWithSealedFindings(caries(16));
        Long noteId = noteService.open(p.patientId(), hoursAgo(1)).id();
        findingService.attend(p.patientId(), noteId, p.findingIds().get(0), CARIES_16);

        assertThatThrownBy(() -> findingService.attend(p.patientId(), noteId, p.findingIds().get(0), "Otra descripción"))
            .isInstanceOf(NoteFindingAlreadyAttendedException.class);
        assertThat(noteService.get(p.patientId(), noteId).attendedFindings())
            .extracting(NoteFindingView::description).containsExactly(CARIES_16);
    }

    @Test
    void a_finding_of_another_patient_is_treated_as_missing() {
        PatientWithFindings other = aPatientWithSealedFindings(caries(26));
        Long patientId = aPatientWithSealedHistory();
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();

        assertThatThrownBy(() -> findingService.attend(patientId, noteId, other.findingIds().get(0), CARIES_16))
            .isInstanceOf(NoteFindingNotFoundException.class);
        assertThatThrownBy(() -> findingService.attend(patientId, noteId, 999_999L, CARIES_16))
            .isInstanceOf(NoteFindingNotFoundException.class);
    }

    @Test
    void a_finding_of_another_clinic_is_treated_as_missing() throws SQLException {
        long otherOrg;
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             var rs = s.executeQuery("INSERT INTO organizations (name, slug) VALUES ('Otra', 'otra-"
                 + UUID.randomUUID() + "') RETURNING id")) {
            rs.next();
            otherOrg = rs.getLong(1);
            // La admisión de la otra clínica también la firma someUser: necesita capacidad clínica ahí.
            ClinicalSignerFixtures.enableSigner(owner, someUser, otherOrg);
        }
        OrganizationContext.set(otherOrg);
        Long foreignFinding = aPatientWithSealedFindings(caries(16)).findingIds().get(0);
        asUser(someUser);
        Long patientId = aPatientWithSealedHistory();
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();

        assertThatThrownBy(() -> findingService.attend(patientId, noteId, foreignFinding, CARIES_16))
            .isInstanceOf(NoteFindingNotFoundException.class);
    }

    @Test
    void attending_or_removing_in_a_sealed_note_is_rejected_and_the_note_does_not_change() {
        PatientWithFindings p = aPatientWithSealedFindings(caries(16), edentulous());
        Long noteId = noteService.open(p.patientId(), hoursAgo(1)).id();
        NoteFindingView attended = findingService.attend(p.patientId(), noteId, p.findingIds().get(0), CARIES_16);
        noteService.update(p.patientId(), noteId, complete(hoursAgo(1)));
        noteDiagnosisService.add(p.patientId(), noteId, diagnosis("K02", TipoDiagnostico.DEFINITIVO));
        noteService.seal(p.patientId(), noteId);

        assertThatThrownBy(() -> findingService.attend(p.patientId(), noteId, p.findingIds().get(1), "Edéntulo total"))
            .isInstanceOf(ConsultationNoteSealedException.class);
        assertThatThrownBy(() -> findingService.remove(p.patientId(), noteId, attended.id()))
            .isInstanceOf(ConsultationNoteSealedException.class);
        ConsultationNoteView sealed = noteService.get(p.patientId(), noteId);
        // La nota sellada se lee sola: con la descripción guardada al marcarla.
        assertThat(sealed.attendedFindings()).extracting(NoteFindingView::description).containsExactly(CARIES_16);
    }

    @Test
    void the_database_rejects_writing_the_attended_findings_of_a_sealed_note() throws SQLException {
        PatientWithFindings p = aPatientWithSealedFindings(caries(16), edentulous());
        Long noteId = noteService.open(p.patientId(), hoursAgo(1)).id();
        NoteFindingView attended = findingService.attend(p.patientId(), noteId, p.findingIds().get(0), CARIES_16);
        noteService.update(p.patientId(), noteId, complete(hoursAgo(1)));
        noteDiagnosisService.add(p.patientId(), noteId, diagnosis("K02", TipoDiagnostico.DEFINITIVO));
        noteService.seal(p.patientId(), noteId);

        assertRejectedAsSealed("INSERT INTO consultation_note_finding (organization_id, note_id, finding_id, description, "
            + "created_by, updated_by) VALUES (" + org + ", " + noteId + ", " + p.findingIds().get(1) + ", 'x', "
            + someUser + ", " + someUser + ")");
        assertRejectedAsSealed("UPDATE consultation_note_finding SET description = 'otra' WHERE id = " + attended.id());
        assertRejectedAsSealed("DELETE FROM consultation_note_finding WHERE id = " + attended.id());
    }

    @Test
    void the_database_requires_a_non_blank_description() throws SQLException {
        PatientWithFindings p = aPatientWithSealedFindings(caries(16));
        Long noteId = noteService.open(p.patientId(), hoursAgo(1)).id();
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            assertThatThrownBy(() -> s.execute("INSERT INTO consultation_note_finding (organization_id, note_id, "
                + "finding_id, description, created_by, updated_by) VALUES (" + org + ", " + noteId + ", "
                + p.findingIds().get(0) + ", '   ', " + someUser + ", " + someUser + ")"))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState()).as("check_violation").isEqualTo("23514"));
        }
    }

    private void assertRejectedAsSealed(String sql) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            assertThatThrownBy(() -> s.execute(sql))
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getSQLState())
                    .as("object_not_in_prerequisite_state, del trigger de V22/V29").isEqualTo("55000"));
        }
    }

    // --- Requirement (MODIFIED): Nota abierta editable y descartable solo vacía ---

    @Test
    void a_note_with_an_attended_finding_cannot_be_discarded() {
        PatientWithFindings p = aPatientWithSealedFindings(caries(16));
        Long noteId = noteService.open(p.patientId(), hoursAgo(1)).id();
        NoteFindingView attended = findingService.attend(p.patientId(), noteId, p.findingIds().get(0), CARIES_16);

        assertThatThrownBy(() -> noteService.discard(p.patientId(), noteId))
            .isInstanceOf(ConsultationNoteNotEmptyException.class);

        findingService.remove(p.patientId(), noteId, attended.id());
        noteService.discard(p.patientId(), noteId);
        assertThat(noteService.list(p.patientId())).isEmpty();
    }
}
