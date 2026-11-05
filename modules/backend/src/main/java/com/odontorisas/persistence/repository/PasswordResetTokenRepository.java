package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Enlaces de recuperación ({@code update-authentication-password-recovery} design.md D2, D3, D5). Las
 * escrituras van siempre con la fila de {@code users} ya bloqueada: el orden es {@code users} →
 * {@code password_reset_tokens}.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Paso 1 del restablecimiento (D5): solo el {@code user_id}, sin bloqueo y sin cargar la entidad, para que
     * la lectura bajo bloqueo del paso 3 sea la primera del contexto de persistencia y no devuelva una copia
     * vieja.
     */
    @Query("select t.userId from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<Long> findUserIdByTokenHash(@Param("tokenHash") String tokenHash);

    /** Lectura sin bloqueo, para {@code check}. */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Paso 3 del restablecimiento (D5): la fila del token con {@code FOR UPDATE}. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /** Enlaces emitidos a la cuenta desde {@code since}, sin importar su estado: se limitan los envíos (D2). */
    @Query("select count(t) from PasswordResetToken t where t.userId = :userId and t.createdAt > :since")
    long countIssuedSince(@Param("userId") Long userId, @Param("since") Instant since);

    /** Anula los vigentes de la cuenta: un enlace nuevo deja sin valor a los anteriores (D3). */
    @Modifying
    @Query("update PasswordResetToken t set t.revokedAt = :now where t.userId = :userId "
        + "and t.usedAt is null and t.revokedAt is null")
    int revokeValidOfUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Anula los vigentes de la cuenta salvo {@code exceptId}, el que se está usando (D5.6). */
    @Modifying
    @Query("update PasswordResetToken t set t.revokedAt = :now where t.userId = :userId "
        + "and t.usedAt is null and t.revokedAt is null and t.id <> :exceptId")
    int revokeOtherValidOfUser(@Param("userId") Long userId, @Param("exceptId") Long exceptId,
                               @Param("now") Instant now);

    /** Limpieza sin tarea programada (D2): al emitir, se borran las filas de la cuenta anteriores a la ventana. */
    @Modifying
    @Query("delete from PasswordResetToken t where t.userId = :userId and t.createdAt <= :before")
    int deleteIssuedBefore(@Param("userId") Long userId, @Param("before") Instant before);
}
