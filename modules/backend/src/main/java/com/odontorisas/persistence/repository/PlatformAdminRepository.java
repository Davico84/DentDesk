package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.PlatformAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {
    boolean existsByUserId(Long userId);
}
