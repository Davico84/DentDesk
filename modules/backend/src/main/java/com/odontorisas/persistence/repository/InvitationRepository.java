package com.odontorisas.persistence.repository;

import com.odontorisas.common.InvitationStatus;
import com.odontorisas.persistence.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByTokenHash(String tokenHash);
    List<Invitation> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, InvitationStatus status);
    boolean existsByOrganizationIdAndEmailIgnoreCaseAndStatus(Long organizationId, String email, InvitationStatus status);

    @Modifying
    @Query("UPDATE Invitation i SET i.status = :status, i.acceptedAt = :acceptedAt "
        + "WHERE i.id = :id AND i.status = 'PENDING' AND i.expiresAt > :now")
    int consume(@Param("id") Long id, @Param("status") InvitationStatus status,
                @Param("acceptedAt") Instant acceptedAt, @Param("now") Instant now);
}
