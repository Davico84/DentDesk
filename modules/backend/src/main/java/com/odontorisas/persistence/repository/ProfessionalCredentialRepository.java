package com.odontorisas.persistence.repository;

import com.odontorisas.persistence.entity.ProfessionalCredential;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProfessionalCredentialRepository extends JpaRepository<ProfessionalCredential, Long> {

    /**
     * {@code SELECT ... FOR SHARE} al firmar (design.md D4): una renovación concurrente espera a que el
     * sellado confirme. Tiene que ser la primera carga de la credencial en la transacción: si ya
     * estuviera en el contexto de persistencia, Hibernate devolvería esa instancia sin bloquear.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select c from ProfessionalCredential c where c.userId = :userId")
    Optional<ProfessionalCredential> lockForShareByUserId(@Param("userId") Long userId);
}
