package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.Membership;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /**
     * Lo que la regla de capacidad clínica necesita de cada miembro de la clínica activa (add-appointments
     * design.md D4), por la función {@code clinic_capacity_facts} de V32: la RLS no deja a un {@code MEMBER} leer
     * las membresías y credenciales ajenas. Sin número COP. {@code userId} {@code null} = todos los miembros.
     */
    @Query(value = "SELECT user_id AS userId, full_name AS fullName, membership_status AS membershipStatus, "
        + "capacity_revoked AS capacityRevoked, licensed_until AS licensedUntil "
        + "FROM clinic_capacity_facts(CAST(:userId AS BIGINT))", nativeQuery = true)
    List<CapacityFacts> capacityFacts(@Param("userId") Long userId);

    /** Fila de {@code clinic_capacity_facts}; {@code licensedUntil} {@code null} = sin credencial declarada. */
    interface CapacityFacts {
        Long getUserId();

        String getFullName();

        String getMembershipStatus();

        Boolean getCapacityRevoked();

        LocalDate getLicensedUntil();
    }
}
