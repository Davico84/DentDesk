package com.odontorisas.common;

import java.util.regex.Pattern;

/**
 * Tipo de documento de identidad del paciente, como <b>conjunto cerrado</b>. Cada tipo lleva su
 * propio <b>formato</b> (la regla vive con el tipo): el documento es {@code string} —nunca
 * numérico: un tipo numérico tiraría los ceros a la izquierda y el pasaporte es alfanumérico— y su
 * validez depende del tipo elegido (design.md D8).
 *
 * <ul>
 *   <li><b>DNI</b> — 8 dígitos (documento nacional).</li>
 *   <li><b>CE</b> — Carné de Extranjería, 9–12 dígitos.</li>
 *   <li><b>PASSPORT</b> — pasaporte, alfanumérico de 6 a 12.</li>
 * </ul>
 */
public enum DocumentType {
    DNI("^\\d{8}$"),
    CE("^\\d{9,12}$"),
    PASSPORT("^[A-Za-z0-9]{6,12}$");

    private final Pattern pattern;

    DocumentType(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    /** {@code true} si el número de documento tiene el formato válido para este tipo. */
    public boolean matches(String document) {
        return document != null && pattern.matcher(document).matches();
    }
}
