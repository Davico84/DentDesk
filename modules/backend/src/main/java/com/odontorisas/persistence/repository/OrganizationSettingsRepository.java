package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.OrganizationSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, Long> {

    /**
     * {@code SELECT ... FOR UPDATE} de la configuración para reemplazar el horario de atención
     * (add-appointments design.md D5): dos administradores a la vez, el último gana sin mezclar semanas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM OrganizationSettings s WHERE s.organizationId = :organizationId")
    Optional<OrganizationSettings> findForUpdate(@Param("organizationId") Long organizationId);
}
