package com.odontorisas.persistence.repository;

import com.odontorisas.common.InvitationStatus;
import com.odontorisas.persistence.entity.Invitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByTokenHash(String tokenHash);
    List<Invitation> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, InvitationStatus status);
    List<Invitation> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    boolean existsByOrganizationIdAndEmailIgnoreCaseAndStatus(Long organizationId, String email, InvitationStatus status);

    /*
     * Cargas con bloqueo (add-staff design D7, docs/backend.md §8.3.1): reenviar, revocar y verificar escriben la
     * fila, e Hibernate escribe todas sus columnas con lo que leyó. Tienen que ser la PRIMERA lectura de la
     * invitación en la transacción y el estado se comprueba después de bloquear.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.id = :id")
    Optional<Invitation> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invitation i where i.tokenHash = :tokenHash")
    Optional<Invitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    /**
     * Consume la invitación solo si sigue PENDIENTE, vigente y con el MISMO token con que se validó: un reenvío
     * confirmado mientras el registro esperaba cambia el hash y deja el enlace viejo sin efecto (design D7).
     */
    @Modifying
    @Query("UPDATE Invitation i SET i.status = :status, i.acceptedAt = :acceptedAt "
        + "WHERE i.id = :id AND i.tokenHash = :tokenHash AND i.status = 'PENDING' AND i.expiresAt > :now")
    int consume(@Param("id") Long id, @Param("tokenHash") String tokenHash, @Param("status") InvitationStatus status,
                @Param("acceptedAt") Instant acceptedAt, @Param("now") Instant now);
}
