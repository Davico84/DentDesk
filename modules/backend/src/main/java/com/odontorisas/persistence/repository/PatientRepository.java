package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.Patient;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Acceso a {@link Patient}. Todas las consultas quedan restringidas a la organización del
 * contexto <b>automáticamente</b> (Hibernate {@code @TenantId} + Row-Level Security): este
 * repositorio <b>no lleva ni una condición de organización</b>, y no debe llevarla.
 *
 * <p>{@link JpaSpecificationExecutor} habilita la búsqueda dinámica del listado (por nombre,
 * documento y estado) sin escribir consultas a mano — ver {@code PatientSpecifications}.
 */
public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    /** La ficha existente que colisiona con este documento en la clínica (para enlazarla, D4). */
    Optional<Patient> findByDocument(String document);

    /** Igual, para la edición: la <b>otra</b> ficha (distinta de {@code id}) que usa el documento. */
    Optional<Patient> findByDocumentAndIdNot(String document, Long id);

    /** ¿Cuántos pacientes de la clínica usan este teléfono? (aviso de número compartido, D1, alta). */
    long countByPhone(String phone);

    /** Igual, para la edición: cuenta los <b>otros</b> pacientes (excluye al propio {@code id}, D1). */
    long countByPhoneAndIdNot(String phone, Long id);

    /** {@code SELECT ... FOR UPDATE}: primer eslabón del orden de bloqueo del cierre de admisión
     *  (paciente → historia → odontograma, design.md D4 de `add-dental-chart`). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.id = :id")
    Optional<Patient> lockById(@Param("id") Long id);
}
