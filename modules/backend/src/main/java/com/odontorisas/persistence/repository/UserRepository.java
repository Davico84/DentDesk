package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Incremento atómico de fallos de login (design.md D7, update-authentication): una sola
     * sentencia, sin read-modify-write en la app, así que N fallos concurrentes sobre la misma
     * identidad cuentan N (Postgres serializa los UPDATE sobre la misma fila). Si la ventana de
     * bloqueo ya expiró ({@code lockedUntil <= now}), reinicia el contador a 1 en vez de sumar
     * (el conteo parte de cero tras expirar, D3); si no, incrementa y bloquea al alcanzar el
     * umbral. Debe invocarse en una transacción propia ({@code LoginAttemptService}) para
     * sobrevivir al rollback del 401 que lanza {@code AuthService.login}.
     */
    @Modifying
    @Query("""
        UPDATE User u
        SET u.failedLoginAttempts = CASE
              WHEN u.lockedUntil IS NOT NULL AND u.lockedUntil <= :now THEN 1
              ELSE u.failedLoginAttempts + 1 END,
            u.lockedUntil = CASE
              WHEN u.lockedUntil IS NOT NULL AND u.lockedUntil <= :now THEN NULL
              WHEN u.failedLoginAttempts + 1 >= :maxAttempts THEN :until
              ELSE u.lockedUntil END
        WHERE u.id = :id
        """)
    int recordFailedLogin(@Param("id") Long id, @Param("now") Instant now,
                          @Param("maxAttempts") int maxAttempts, @Param("until") Instant until);

    /**
     * Reset por login exitoso (design.md D7): a propósito NO va en transacción propia — debe
     * vivir en la transacción de {@code login} para que un fallo posterior (p. ej. rechazo por
     * estado en {@code activeMembership}) la revierta junto con el resto. Invertir esto
     * permitiría a una identidad no activa limpiar su contador con solo acertar la contraseña.
     *
     * <p><b>Condicional</b> (design.md D13): {@code login} lee {@code lockedUntil} una sola vez
     * al inicio, antes de verificar la contraseña; entre esa lectura y este punto, otra request
     * concurrente puede haber bloqueado la cuenta (p. ej. un flood de intentos fallidos de
     * alguien más). La condición {@code WHERE ... AND (lockedUntil IS NULL OR lockedUntil <=
     * :now)} hace que el UPDATE afecte 0 filas si eso pasó, en cuyo caso {@code login} debe
     * rechazar en vez de emitir tokens — sin este chequeo, una sesión se emitiría para una
     * cuenta que ya está bloqueada. No abre la puerta a un atacante sin la contraseña correcta
     * (BCrypt ya se verificó antes, por request); solo cierra la ventana para quien SÍ la tiene.
     */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = null " +
        "WHERE u.id = :id AND (u.lockedUntil IS NULL OR u.lockedUntil <= :now)")
    int resetLoginAttemptsIfNotLocked(@Param("id") Long id, @Param("now") Instant now);
}
