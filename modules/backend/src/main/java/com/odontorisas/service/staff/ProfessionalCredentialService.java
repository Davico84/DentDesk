package com.odontorisas.service.staff;

import com.odontorisas.common.IdentityContext;
import com.odontorisas.persistence.entity.ProfessionalCredential;
import com.odontorisas.persistence.repository.ProfessionalCredentialRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Credencial profesional del usuario de la sesión ({@code update-staff-professional-credential} design.md
 * D1/D6). El usuario sale siempre de la sesión, nunca del cuerpo ni de la ruta: no hay forma de nombrar a
 * otro, y la RLS lo garantiza además en la base.
 */
@Service
public class ProfessionalCredentialService {

    /** Nombre del índice único de V30; se reconoce por él para no exponer la causa SQL. */
    static final String COP_UNIQUE_CONSTRAINT = "ux_professional_credential_cop_number";

    private final ProfessionalCredentialRepository credentials;
    private final ClinicalCapacityService capacity;
    private final Clock clock;

    public ProfessionalCredentialService(ProfessionalCredentialRepository credentials,
                                         ClinicalCapacityService capacity, Clock clock) {
        this.credentials = credentials;
        this.capacity = capacity;
        this.clock = clock;
    }

    public record CredentialView(String copNumber, LocalDate licensedUntil, Instant swornAt) {
        static CredentialView from(ProfessionalCredential c) {
            return new CredentialView(c.getCopNumber(), c.getLicensedUntil(), c.getSwornAt());
        }
    }

    /** La credencial propia y la capacidad en la clínica activa (vacía sin clínica activa). */
    public record MyCredentialView(Optional<CredentialView> credential,
                                   Optional<ClinicalCapacityService.CapacityView> clinicalCapacity) {}

    @Transactional(readOnly = true)
    public MyCredentialView mine() {
        Optional<CredentialView> credential = credentials.findById(IdentityContext.get()).map(CredentialView::from);
        return new MyCredentialView(credential, capacity.current());
    }

    /**
     * Declara o renueva la credencial propia. Primera vez = {@code INSERT}; después, {@code UPDATE} de la
     * fila cargada (sin upsert, design.md D1). La declaración jurada la exige la validación del DTO.
     *
     * @throws LicensedUntilInPastException   si la fecha es anterior a hoy en Perú
     * @throws CredentialNumberTakenException si el número es de otro usuario
     */
    @Transactional
    public MyCredentialView declare(String copNumber, LocalDate licensedUntil) {
        if (licensedUntil.isBefore(capacity.todayInPeru())) {
            throw new LicensedUntilInPastException();
        }
        Long userId = IdentityContext.get();
        Instant now = clock.instant();
        ProfessionalCredential credential = credentials.findById(userId)
            .map(existing -> {
                existing.setCopNumber(copNumber);
                existing.setLicensedUntil(licensedUntil);
                existing.setSwornAt(now);
                return existing;
            })
            .orElseGet(() -> new ProfessionalCredential(userId, copNumber, licensedUntil, now));
        try {
            credentials.saveAndFlush(credential);
        } catch (DataIntegrityViolationException e) {
            if (isCopUniqueViolation(e)) {
                // Sin encadenar la causa: su mensaje es el de PostgreSQL.
                throw new CredentialNumberTakenException();
            }
            throw e;
        }
        return mine();
    }

    private static boolean isCopUniqueViolation(DataIntegrityViolationException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException cve
                && COP_UNIQUE_CONSTRAINT.equals(cve.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
