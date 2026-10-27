package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.Membership;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /**
     * Membresías de una identidad. La Row-Level Security de identidad garantiza en el
     * motor que solo devuelve las del usuario del contexto; el parámetro {@code userId}
     * es, además, el filtro de la capa de aplicación.
     */
    List<Membership> findByUserId(Long userId);

    List<Membership> findByOrganizationId(Long organizationId);

    Optional<Membership> findByUserIdAndOrganizationId(Long userId, Long organizationId);

    /**
     * {@code SELECT ... FOR SHARE} al firmar ({@code update-staff-professional-credential} design.md D4): un
     * retiro, una restitución o una desactivación concurrentes esperan a que el sellado confirme, y si
     * confirmaron antes, esta lectura ve su versión. Tiene que ser la primera carga de la membresía en
     * la transacción.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select m from Membership m where m.user.id = :userId and m.organization.id = :organizationId")
    Optional<Membership> lockForShare(@Param("userId") Long userId, @Param("organizationId") Long organizationId);
}
