package com.odontorisas.consultationnote;

import com.odontorisas.ClinicalSignerFixtures;
import com.odontorisas.common.ClinicalCapacityState;
import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.common.DentalChartBlockStatus;
import com.odontorisas.common.HistoriaClinicaStatus;
import com.odontorisas.common.IdentityContext;
import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.common.SignerCredential;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.service.auth.ProfileService;
import com.odontorisas.service.clinicalhistory.ClinicalHistoryView;
import com.odontorisas.service.consultationnote.ConsultationNoteView;
import com.odontorisas.service.dentalchart.DentalChartBlockView;
import com.odontorisas.service.staff.ClinicalCapacityRequiredException;
import com.odontorisas.service.staff.ClinicalCapacityService;
import com.odontorisas.service.staff.ProfessionalCredentialService;
import com.odontorisas.service.staff.StaffService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Firmar con credencial: cerrar la admisión y sellar la nota exigen capacidad clínica, y lo firmado guarda la
 * foto de la credencial ({@code update-staff-professional-credential} design.md D3–D5). Scenarios de
 * {@code clinical-history}: <i>Firmar exige capacidad clínica</i>, <i>Credencial del firmante fotografiada al
 * sellar</i> y <i>Firmas anteriores al registro de credenciales</i>; de {@code dental-chart}: <i>Credencial
 * del firmante en el odontograma sellado</i>.
 *
 * <p>Las carreras (tarea 4.5) usan hilos propios y la señal {@code awaitLockWaitOrFinished}: con el
 * {@code FOR SHARE} de {@code require()} el retiro, la desactivación o la renovación ESPERAN a que el sellado
 * confirme; sin él terminan antes, y el test lo detecta.
 */
@SpringBootTest
class SigningWithCredentialIT extends AbstractConsultationNoteIT {

    @Autowired ClinicalCapacityService capacityService;
    @Autowired ProfessionalCredentialService credentialService;
    @Autowired StaffService staffService;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ProfileService profileService;

    private long admin;
    private ExecutorService pool;

    @BeforeEach
    void anAdminAndAPool() throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            admin = id(s, "INSERT INTO users (email, password_hash, full_name) VALUES ('sw-admin-" + UUID.randomUUID()
                + "@test.local', 'x', 'Admin') RETURNING id");
            s.execute("INSERT INTO memberships (user_id, organization_id, role, status) VALUES (" + admin + ", " + org
                + ", 'ADMIN', 'ACTIVE')");
            s.execute("UPDATE users SET full_name = 'Dra. Firmante' WHERE id = " + someUser);
        }
        // Hilos propios: el pool común de CompletableFuture es de un hilo en CI.
        pool = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void stopPool() {
        pool.shutdownNow();
    }

    private static long id(Statement s, String sql) throws SQLException {
        try (ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void sql(String statement) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement()) {
            s.execute(statement);
        }
    }

    private String query(String select) throws SQLException {
        try (Connection owner = ownerConnection(); Statement s = owner.createStatement();
             ResultSet rs = s.executeQuery(select)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private LocalDate licensedUntilOf(long user) throws SQLException {
        return LocalDate.parse(query("SELECT licensed_until::text FROM professional_credential WHERE user_id = " + user));
    }

    private void setLicensedUntil(long user, LocalDate date) throws SQLException {
        sql("UPDATE professional_credential SET licensed_until = '" + date + "' WHERE user_id = " + user);
    }

    private void revokeAsAdmin(long member) throws SQLException {
        sql("SELECT set_config('app.user_id', '" + admin + "', false); UPDATE memberships SET "
            + "clinical_capacity_revoked_at = now(), clinical_capacity_revoked_by = " + admin + " WHERE user_id = "
            + member + " AND organization_id = " + org);
    }

    /** Paciente con Historia y odontograma listos para cerrar la admisión, sin cerrarla. */
    private Long aPatientReadyToClose() {
        Long patientId = aPatient();
        clinicalHistoryService.create(patientId);
        clinicalHistoryService.update(patientId, FULL_HC);
        historyDiagnosisService.add(patientId, diagnosis(HC_CODE, TipoDiagnostico.DEFINITIVO));
        dentalChartService.createInitial(patientId, LocalDate.now());
        dentalChartService.setSpecifications(patientId, null);
        dentalChartService.setObservations(patientId, null);
        return patientId;
    }

    /** Nota ABIERTA con todo lo que exige el sellado. */
    private Long anOpenNoteReadyToSeal(Long patientId) {
        Long noteId = noteService.open(patientId, hoursAgo(1)).id();
        noteService.update(patientId, noteId, complete(hoursAgo(1)));
        noteDiagnosisService.add(patientId, noteId, diagnosis(HC_CODE, TipoDiagnostico.DEFINITIVO));
        return noteId;
    }

    private static ClinicalCapacityState reasonOf(Throwable e) {
        assertThat(e).isInstanceOf(ClinicalCapacityRequiredException.class);
        return ((ClinicalCapacityRequiredException) e).getReason();
    }

    // ================================================================ Firmar exige capacidad clínica

    @Test
    void closing_the_admission_with_capacity_stores_the_same_snapshot_in_history_and_chart() throws SQLException {
        Long patientId = aPatientReadyToClose();
        LocalDate until = licensedUntilOf(someUser);

        var closed = closeAdmissionService.close(patientId);

        SignerCredential expected = new SignerCredential("Dra. Firmante",
            query("SELECT cop_number FROM professional_credential WHERE user_id = " + someUser), until);
        assertThat(closed.clinicalHistory().signer()).isEqualTo(expected);
        assertThat(closed.dentalChart().signer()).isEqualTo(expected);
        assertThat(query("SELECT completed_by::text FROM historia_clinica WHERE patient_id = " + patientId))
            .isEqualTo(String.valueOf(someUser));
        assertThat(query("SELECT sealed_by::text FROM dental_chart_block WHERE patient_id = " + patientId))
            .isEqualTo(String.valueOf(someUser));
        assertThat(closed.clinicalHistory().signedBeforeCredentials()).isFalse();
    }

    @Test
    void closing_the_admission_without_a_credential_is_denied_and_nothing_is_sealed() throws SQLException {
        Long patientId = aPatientReadyToClose();
        sql("DELETE FROM professional_credential WHERE user_id = " + someUser);

        assertThatThrownBy(() -> closeAdmissionService.close(patientId))
            .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ClinicalCapacityState.NO_CREDENTIAL));

        assertThat(clinicalHistoryService.getByPatientId(patientId).status()).isEqualTo(HistoriaClinicaStatus.DRAFT);
        assertThat(dentalChartService.getByPatientId(patientId).status()).isEqualTo(DentalChartBlockStatus.OPEN);
    }

    @Test
    void sealing_a_note_with_an_expired_licence_is_denied_with_the_date() throws SQLException {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = anOpenNoteReadyToSeal(patientId);
        LocalDate yesterday = capacityService.todayInPeru().minusDays(1);
        setLicensedUntil(someUser, yesterday);

        assertThatThrownBy(() -> noteService.seal(patientId, noteId))
            .satisfies(e -> {
                assertThat(reasonOf(e)).isEqualTo(ClinicalCapacityState.EXPIRED);
                assertThat(((ClinicalCapacityRequiredException) e).getExpiredOn()).isEqualTo(yesterday);
            });
        ConsultationNoteView note = noteService.get(patientId, noteId);
        assertThat(note.status()).isEqualTo(ConsultationNoteStatus.ABIERTA);
        assertThat(note.examination()).isEqualTo("Encía sana");
    }

    @Test
    void sealing_a_note_with_the_capacity_revoked_by_the_clinic_is_denied() throws SQLException {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = anOpenNoteReadyToSeal(patientId);
        revokeAsAdmin(someUser);

        assertThatThrownBy(() -> noteService.seal(patientId, noteId))
            .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ClinicalCapacityState.REVOKED));
    }

    @Test
    void without_capacity_drafts_are_still_saved() throws SQLException {
        Long patientId = aPatientReadyToClose();
        Long withNote = aPatientWithSealedHistory();
        Long noteId = noteService.open(withNote, hoursAgo(1)).id();
        sql("DELETE FROM professional_credential WHERE user_id = " + someUser);

        clinicalHistoryService.update(patientId, FULL_HC);
        noteService.update(withNote, noteId, complete(hoursAgo(1)));

        assertThat(noteService.get(withNote, noteId).treatment()).isEqualTo("Profilaxis");
    }

    // ================================================================ Credencial del firmante fotografiada

    @Test
    void a_sealed_note_keeps_the_signer_as_it_was() throws SQLException {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = anOpenNoteReadyToSeal(patientId);
        LocalDate until = licensedUntilOf(someUser);
        noteService.seal(patientId, noteId);

        // Renovar, corregir el nombre, vencer y ser retirado después no cambian lo firmado.
        setLicensedUntil(someUser, until.plusYears(2));
        sql("UPDATE users SET full_name = 'Dra. Firmante Corregida' WHERE id = " + someUser);
        setLicensedUntil(someUser, capacityService.todayInPeru().minusDays(1));
        revokeAsAdmin(someUser);

        ConsultationNoteView note = noteService.get(patientId, noteId);
        assertThat(note.status()).isEqualTo(ConsultationNoteStatus.SELLADA);
        assertThat(note.signer().fullName()).isEqualTo("Dra. Firmante");
        assertThat(note.signer().licensedUntil()).isEqualTo(until);
        assertThat(note.signedBeforeCredentials()).isFalse();
    }

    /**
     * {@code authentication}, <i>Lo ya firmado conserva el nombre de entonces</i>
     * ({@code update-authentication-my-profile}): el nombre se corrige por el perfil propio, no por SQL.
     */
    @Test
    void correcting_the_own_name_keeps_signed_notes_and_new_signatures_carry_the_new_one() {
        Long patientId = aPatientWithSealedHistory();
        Long before = anOpenNoteReadyToSeal(patientId);
        noteService.seal(patientId, before);

        profileService.updateFullName("Dra. Firmante Corregida");

        Long after = anOpenNoteReadyToSeal(patientId);
        noteService.seal(patientId, after);

        assertThat(noteService.get(patientId, before).signer().fullName()).isEqualTo("Dra. Firmante");
        assertThat(noteService.get(patientId, after).signer().fullName()).isEqualTo("Dra. Firmante Corregida");
    }

    // ================================================================ Firmas anteriores al registro

    @Test
    void history_chart_and_note_sealed_before_the_change_are_marked_as_such() throws SQLException {
        Long patientId = aPatientReadyToClose();
        Long withNote = aPatientWithSealedHistory();
        Long noteId = anOpenNoteReadyToSeal(withNote);
        try (Connection owner = ownerConnection()) {
            ClinicalSignerFixtures.asBeforeTheConstraint(owner, "historia_clinica", "historia_clinica_signer_required",
                "UPDATE historia_clinica SET status = 'COMPLETED', completed_at = now() WHERE patient_id = " + patientId);
            ClinicalSignerFixtures.asBeforeTheConstraint(owner, "dental_chart_block", "dental_chart_block_signer_required",
                "UPDATE dental_chart_block SET status = 'SEALED', sealed_at = now() WHERE patient_id = " + patientId);
            ClinicalSignerFixtures.asBeforeTheConstraint(owner, "consultation_note", "consultation_note_signer_required",
                "UPDATE consultation_note SET status = 'SELLADA', sealed_at = now(), sealed_by = " + someUser
                    + " WHERE id = " + noteId);
        }

        ClinicalHistoryView history = clinicalHistoryService.getByPatientId(patientId);
        DentalChartBlockView chart = dentalChartService.getByPatientId(patientId);
        ConsultationNoteView note = noteService.get(withNote, noteId);
        assertThat(history.signedBeforeCredentials()).isTrue();
        assertThat(history.signer()).isNull();
        assertThat(chart.signedBeforeCredentials()).isTrue();
        assertThat(note.signedBeforeCredentials()).isTrue();
        // Declarar la credencial después no les completa nada.
        assertThat(noteService.get(withNote, noteId).signer()).isNull();
    }

    // ================================================================ Carreras (tarea 4.5)

    /**
     * El sellado corre en una transacción que se mantiene abierta hasta que la otra operación termina o queda
     * esperando un bloqueo. Devuelve si la otra operación ya había terminado en ese momento: con el
     * {@code FOR SHARE} tiene que estar esperando (false); sin él, termina antes (true).
     */
    private boolean otherFinishedBeforeTheSealCommitted(Runnable seal, Supplier<Throwable> other) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch sealedButNotCommitted = new CountDownLatch(1);
        CountDownLatch otherFinished = new CountDownLatch(1);
        AtomicBoolean finishedFirst = new AtomicBoolean();

        Future<?> sealing = pool.submit(() -> {
            asUser(someUser);
            try {
                tx.executeWithoutResult(status -> {
                    seal.run();
                    sealedButNotCommitted.countDown();
                    awaitLockWaitOrFinished(otherFinished);
                    finishedFirst.set(otherFinished.getCount() == 0);
                });
            } finally {
                IdentityContext.clear();
                OrganizationContext.clear();
            }
        });
        Future<Throwable> concurrent = pool.submit(() -> {
            try {
                assertThat(sealedButNotCommitted.await(10, TimeUnit.SECONDS)).isTrue();
                return other.get();
            } finally {
                otherFinished.countDown();
                IdentityContext.clear();
                OrganizationContext.clear();
            }
        });
        sealing.get(30, TimeUnit.SECONDS);
        assertThat(concurrent.get(30, TimeUnit.SECONDS)).as("la operación concurrente termina sin error").isNull();
        return finishedFirst.get();
    }

    private Supplier<Throwable> asAdmin(Runnable action) {
        return () -> {
            IdentityContext.set(admin);
            OrganizationContext.set(org);
            action.run();
            return null;
        };
    }

    @Test
    void a_revocation_while_a_note_is_being_sealed_waits_for_the_seal() throws Exception {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = anOpenNoteReadyToSeal(patientId);

        boolean revokedFirst = otherFinishedBeforeTheSealCommitted(() -> noteService.seal(patientId, noteId),
            asAdmin(() -> staffService.changeClinicalCapacity(someUser, true)));

        assertThat(revokedFirst).as("el retiro espera al sellado en curso (FOR SHARE)").isFalse();
        assertThat(noteService.get(patientId, noteId).status()).isEqualTo(ConsultationNoteStatus.SELLADA);
        assertThat(capacityStateOf(someUser)).isEqualTo(ClinicalCapacityState.REVOKED);
    }

    @Test
    void a_revocation_while_an_admission_is_being_closed_waits_for_the_closing() throws Exception {
        Long patientId = aPatientReadyToClose();

        boolean revokedFirst = otherFinishedBeforeTheSealCommitted(() -> closeAdmissionService.close(patientId),
            asAdmin(() -> staffService.changeClinicalCapacity(someUser, true)));

        assertThat(revokedFirst).isFalse();
        assertThat(clinicalHistoryService.getByPatientId(patientId).status()).isEqualTo(HistoriaClinicaStatus.COMPLETED);
    }

    @Test
    void a_deactivation_while_a_note_is_being_sealed_waits_for_the_seal() throws Exception {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = anOpenNoteReadyToSeal(patientId);

        boolean deactivatedFirst = otherFinishedBeforeTheSealCommitted(() -> noteService.seal(patientId, noteId),
            asAdmin(() -> staffService.changeStatus(someUser, MembershipStatus.INACTIVE)));

        assertThat(deactivatedFirst).isFalse();
        assertThat(noteService.get(patientId, noteId).status()).isEqualTo(ConsultationNoteStatus.SELLADA);
    }

    @Test
    void a_revocation_confirmed_before_the_seal_rejects_it() throws Exception {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = anOpenNoteReadyToSeal(patientId);
        IdentityContext.set(admin);
        staffService.changeClinicalCapacity(someUser, true);
        asUser(someUser);

        assertThatThrownBy(() -> noteService.seal(patientId, noteId))
            .satisfies(e -> assertThat(reasonOf(e)).isEqualTo(ClinicalCapacityState.REVOKED));
    }

    @Test
    void a_renewal_while_a_note_is_being_sealed_waits_and_the_snapshot_keeps_the_confirmed_date() throws Exception {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = anOpenNoteReadyToSeal(patientId);
        LocalDate confirmed = licensedUntilOf(someUser);
        String cop = query("SELECT cop_number FROM professional_credential WHERE user_id = " + someUser);

        boolean renewedFirst = otherFinishedBeforeTheSealCommitted(() -> noteService.seal(patientId, noteId), () -> {
            asUser(someUser);
            credentialService.declare(cop, confirmed.plusYears(1));
            return null;
        });

        assertThat(renewedFirst).as("la renovación espera al sellado en curso (FOR SHARE)").isFalse();
        assertThat(noteService.get(patientId, noteId).signer().licensedUntil()).isEqualTo(confirmed);
        assertThat(licensedUntilOf(someUser)).isEqualTo(confirmed.plusYears(1));
    }

    @Test
    void two_seals_of_the_same_professional_do_not_block_each_other() throws Exception {
        Long first = aPatientWithSealedHistory();
        Long firstNote = anOpenNoteReadyToSeal(first);
        Long second = aPatientWithSealedHistory();
        Long secondNote = anOpenNoteReadyToSeal(second);

        boolean secondFinishedFirst = otherFinishedBeforeTheSealCommitted(() -> noteService.seal(first, firstNote), () -> {
            asUser(someUser);
            new TransactionTemplate(transactionManager).executeWithoutResult(s -> noteService.seal(second, secondNote));
            return null;
        });

        assertThat(secondFinishedFirst).as("FOR SHARE no bloquea otra firma del mismo profesional").isTrue();
        assertThat(noteService.get(second, secondNote).status()).isEqualTo(ConsultationNoteStatus.SELLADA);
    }

    private ClinicalCapacityState capacityStateOf(long user) {
        asUser(user);
        return capacityService.current().orElseThrow().state();
    }
}
