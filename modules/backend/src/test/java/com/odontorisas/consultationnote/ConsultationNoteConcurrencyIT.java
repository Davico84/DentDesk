package com.odontorisas.consultationnote;

import com.odontorisas.common.ConsultationNoteStatus;
import com.odontorisas.common.TipoDiagnostico;
import com.odontorisas.service.consultationnote.ConsultationNoteAlreadyOpenException;
import com.odontorisas.service.consultationnote.ConsultationNoteData;
import com.odontorisas.service.consultationnote.ConsultationNoteSealedException;
import com.odontorisas.service.consultationnote.ConsultationNoteView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrencia de la nota de consulta (design.md D1/D2), con hilos reales contra PostgreSQL:
 * <ul>
 *   <li>aperturas en paralelo: una sola nota (índice parcial {@code ux_consultation_note_open});</li>
 *   <li>escritura durante el sellado, con el orden FORZADO como {@code ClinicalHistoryConcurrencyIT}:
 *       A sella dentro de una transacción y no confirma hasta que B queda esperando el bloqueo de la
 *       nota ({@code pg_locks}). B tiene que ver la nota sellada y fallar, no reabrirla.</li>
 * </ul>
 */
@SpringBootTest
class ConsultationNoteConcurrencyIT extends AbstractConsultationNoteIT {

    @Autowired
    private PlatformTransactionManager transactionManager;

    // --- scenario: Apertura concurrente de notas ---

    @Test
    void concurrent_openings_create_a_single_note() throws Exception {
        Long patientId = aPatientWithSealedHistory();
        Instant attendedAt = hoursAgo(1);
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Throwable>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    asUser(someUser);
                    try {
                        start.await();
                        noteService.open(patientId, attendedAt);
                        return null;
                    } catch (Throwable e) {
                        return e;
                    } finally {
                        clearContext();
                    }
                }));
            }
            start.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> r : results) {
                outcomes.add(r.get(30, TimeUnit.SECONDS));
            }
            assertThat(outcomes.stream().filter(e -> e == null)).hasSize(1);
            assertThat(outcomes.stream().filter(e -> e != null))
                .allSatisfy(e -> assertThat(e).isInstanceOf(ConsultationNoteAlreadyOpenException.class));
        } finally {
            pool.shutdownNow();
        }
        asUser(someUser);
        assertThat(noteService.list(patientId)).hasSize(1);
    }

    // --- scenario: Una escritura concurrente no altera la nota que se sella ---

    private Throwable whileSealing(Long patientId, Long noteId, Callable<?> write) throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch writeFinished = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> sealer = pool.submit(() -> {
                asUser(someUser);
                try {
                    tx.executeWithoutResult(status -> {
                        noteService.seal(patientId, noteId);
                        locked.countDown();
                        awaitLockWaitOrFinished(writeFinished);
                    });
                } finally {
                    clearContext();
                }
            });
            Future<Throwable> writer = pool.submit(() -> {
                asUser(someUser);
                try {
                    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                    write.call();
                    return null;
                } catch (Throwable e) {
                    return e;
                } finally {
                    writeFinished.countDown();
                    clearContext();
                }
            });
            sealer.get(30, TimeUnit.SECONDS);
            return writer.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private Long aNoteReadyToSeal(Long patientId, Instant attendedAt) {
        Long noteId = noteService.open(patientId, attendedAt).id();
        noteService.update(patientId, noteId, complete(attendedAt));
        noteDiagnosisService.add(patientId, noteId, diagnosis("K02", TipoDiagnostico.DEFINITIVO));
        return noteId;
    }

    @Test
    void saving_a_field_while_the_note_is_being_sealed_is_rejected_and_the_note_stays_sealed() throws Exception {
        Long patientId = aPatientWithSealedHistory();
        Instant attendedAt = hoursAgo(1);
        Long noteId = aNoteReadyToSeal(patientId, attendedAt);

        Throwable result = whileSealing(patientId, noteId, () -> noteService.update(patientId, noteId,
            new ConsultationNoteData(attendedAt, "Cambio tardío", null, null, null, null, "Otro examen",
                "Profilaxis", null, null, null)));

        assertThat(result).isInstanceOf(ConsultationNoteSealedException.class);
        ConsultationNoteView note = noteService.get(patientId, noteId);
        assertThat(note.status()).isEqualTo(ConsultationNoteStatus.SELLADA);
        assertThat(note.examination()).isEqualTo("Encía sana");
    }

    @Test
    void adding_a_diagnosis_while_the_note_is_being_sealed_is_rejected() throws Exception {
        Long patientId = aPatientWithSealedHistory();
        Long noteId = aNoteReadyToSeal(patientId, hoursAgo(1));

        Throwable result = whileSealing(patientId, noteId,
            () -> noteDiagnosisService.add(patientId, noteId, diagnosis("K05", TipoDiagnostico.PRESUNTIVO)));

        assertThat(result).isInstanceOf(ConsultationNoteSealedException.class);
        assertThat(noteService.get(patientId, noteId).diagnoses()).extracting(d -> d.cie10Code()).containsExactly("K02");
    }
}
