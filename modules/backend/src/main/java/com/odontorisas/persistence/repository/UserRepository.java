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
     *
     * <p><b>Exige el hash verificado</b> ({@code update-authentication-my-profile} design.md D3.2): si un
     * cambio de contraseña commitea entre el BCrypt del login y este punto, el hash ya no es el verificado y
     * el UPDATE afecta 0 filas: el login se rechaza en vez de emitir una sesión con la contraseña anterior
     * que sobreviviría al cierre de sesiones. El UPDATE toma el bloqueo de la fila hasta el commit, y en
     * {@code READ COMMITTED} Postgres re-evalúa el {@code WHERE} sobre la versión nueva si tuvo que esperar.
     */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = null " +
        "WHERE u.id = :id AND u.passwordHash = :verifiedHash " +
        "AND (u.lockedUntil IS NULL OR u.lockedUntil <= :now)")
    int resetLoginAttemptsIfNotLocked(@Param("id") Long id, @Param("verifiedHash") String verifiedHash,
                                      @Param("now") Instant now);

    /**
     * Bloquea la fila con {@code FOR UPDATE} y devuelve el hash <b>releído de la base</b>, solo si la cuenta
     * no está bloqueada ({@code update-authentication-my-profile} design.md D3.1). Consulta nativa a
     * propósito: una consulta de entidad devolvería la instancia ya cargada en el contexto de persistencia
     * sin refrescar su estado, y el re-chequeo compararía contra un hash viejo. Vacío = bloqueada (o
     * inexistente); en ese caso no queda bloqueo tomado.
     */
    @Query(value = "SELECT password_hash FROM users WHERE id = :id "
        + "AND (locked_until IS NULL OR locked_until <= :now) FOR UPDATE", nativeQuery = true)
    Optional<String> lockPasswordHashIfNotLocked(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Bloquea la fila con {@code FOR SHARE} antes de que {@code refresh} consuma su token
     * ({@code update-authentication-my-profile} design.md D3.3): serializa la renovación con un cambio de
     * contraseña, que toma {@code FOR UPDATE}. Renovaciones entre sí no compiten.
     */
    @Query(value = "SELECT id FROM users WHERE id = :id FOR SHARE", nativeQuery = true)
    Optional<Long> lockForShare(@Param("id") Long id);

    /**
     * Contraseña nueva y contador a cero, con {@code locked_until} en NULL (design.md D4). Bulk update: no
     * pasa por la entidad cargada, que puede estar vieja; la fila ya está bloqueada por
     * {@link #lockPasswordHashIfNotLocked}.
     */
    @Modifying
    @Query("UPDATE User u SET u.passwordHash = :hash, u.failedLoginAttempts = 0, u.lockedUntil = null, "
        + "u.updatedAt = :now WHERE u.id = :id")
    int changePassword(@Param("id") Long id, @Param("hash") String hash, @Param("now") Instant now);
}
