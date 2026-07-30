package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.Cie10Code;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acceso al catálogo CIE10 (design.md D6). Sin {@code @TenantId}: es un catálogo global, no una
 * entidad de negocio — no lleva ni necesita aislamiento por clínica.
 */
public interface Cie10CodeRepository extends JpaRepository<Cie10Code, String> {

    /** Búsqueda por texto en la descripción (case-insensitive, substring). */
    List<Cie10Code> findByDescripcionContainingIgnoreCase(String texto);
}
