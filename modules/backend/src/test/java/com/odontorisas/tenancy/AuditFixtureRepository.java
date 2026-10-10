package com.odontorisas.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditFixtureRepository extends JpaRepository<AuditFixtureEntity, Long> {
}
