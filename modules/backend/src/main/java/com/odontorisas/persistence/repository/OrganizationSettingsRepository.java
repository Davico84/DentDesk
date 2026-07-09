package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.OrganizationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, Long> {
}
