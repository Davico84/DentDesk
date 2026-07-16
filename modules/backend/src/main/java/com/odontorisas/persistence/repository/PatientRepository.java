package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Acceso a {@link Patient}. Todas las consultas quedan restringidas a la organización del
 * contexto <b>automáticamente</b> (Hibernate {@code @TenantId} + Row-Level Security): este
 * repositorio <b>no lleva ni una condición de organización</b>, y no debe llevarla.
 *
 * <p>{@link JpaSpecificationExecutor} habilita la búsqueda dinámica del listado (por nombre,
 * documento y estado) sin escribir consultas a mano — ver {@code PatientSpecifications}.
 */
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    /** Pre-chequeo de unicidad del documento dentro de la clínica (el índice es la última red). */
    boolean existsByDocument(String document);

    /** Igual, para la edición: ¿otro paciente (distinto de {@code id}) ya usa este documento? */
    boolean existsByDocumentAndIdNot(String document, Long id);
}
