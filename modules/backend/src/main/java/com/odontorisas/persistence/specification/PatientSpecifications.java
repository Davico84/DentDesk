package com.odontorisas.persistence.specification;

import com.odontorisas.common.PatientStatus;
import com.odontorisas.persistence.entity.Patient;
import org.springframework.data.jpa.domain.Specification;

/**
 * Predicados de búsqueda de {@link Patient} para el listado. <b>Ninguno filtra por
 * organización</b>: eso lo añade Hibernate ({@code @TenantId}) + RLS de forma automática, y
 * duplicarlo aquí sería exactamente el "por disciplina" que evitamos.
 */
public final class PatientSpecifications {

    private PatientSpecifications() {
    }

    /** Nombre o documento que contenga {@code query} (insensible a mayúsculas). */
    public static Specification<Patient> matches(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String like = "%" + query.trim().toLowerCase() + "%";
        return (root, cq, cb) -> cb.or(
            cb.like(cb.lower(root.get("fullName")), like),
            cb.like(cb.lower(root.get("document")), like));
    }

    /** Solo pacientes activos (el listado por defecto oculta los dados de baja). */
    public static Specification<Patient> activeOnly() {
        return (root, cq, cb) -> cb.equal(root.get("status"), PatientStatus.ACTIVE);
    }
}
