package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.RefreshToken;
import com.odontorisas.persistence.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);

    /**
     * Borrado atómico por hash (bulk delete) que devuelve el número de filas
     * afectadas. Permite consumir el refresh token de forma segura ante
     * concurrencia: solo un consumidor obtiene {@code 1}; el resto obtiene
     * {@code 0} sin provocar un {@code StaleStateException}.
     */
    @Modifying
    @Query("delete from RefreshToken rt where rt.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Cierra todas las sesiones del usuario, en todas las clínicas ({@code refresh_tokens.user_id} es global):
     * bulk delete, sin cargar las filas ({@code update-authentication-my-profile} design.md D2).
     */
    @Modifying
    @Query("delete from RefreshToken rt where rt.user.id = :userId")
    int deleteAllOfUser(@Param("userId") Long userId);
}
