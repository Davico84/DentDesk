package com.odontorisas.persistence.entity;

import com.odontorisas.common.SignerCredential;
import com.odontorisas.common.SignerSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.LocalDate;

/**
 * Credencial del firmante copiada en un registro sellado: Historia Clínica, odontograma inicial y nota de
 * consulta ({@code update-staff-professional-credential} design.md D5). Nula en lo sellado antes de V30,
 * que se muestra como "firmado antes del registro de credenciales"; un CHECK la exige en todo sellado
 * nuevo. Quién firmó (el id) vive aparte, en la columna propia de cada tabla.
 */
@Embeddable
public record Signature(
    @Column(name = "signer_full_name", length = 120) String fullName,
    @Column(name = "signer_cop_number", length = 10) String copNumber,
    @Column(name = "signer_licensed_until") LocalDate licensedUntil) {

    public static Signature of(SignerSnapshot signer) {
        return new Signature(signer.fullName(), signer.copNumber(), signer.licensedUntil());
    }

    /** La foto leída de vuelta, en el tipo que comparten vistas y DTOs; null si no hay foto. */
    public static SignerCredential read(Signature signature) {
        return signature == null ? null
            : new SignerCredential(signature.fullName(), signature.copNumber(), signature.licensedUntil());
    }
}
