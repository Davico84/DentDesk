package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
