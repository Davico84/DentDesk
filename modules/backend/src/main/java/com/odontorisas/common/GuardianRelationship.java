package com.odontorisas.common;

/**
 * Parentesco del responsable de un paciente menor, como <b>conjunto cerrado</b> (design.md D2).
 * {@code OTHER} cubre abuelos, hermanos mayores, apoderados legales, etc. Es obligatorio cuando
 * el paciente es menor de edad.
 */
public enum GuardianRelationship {
    MOTHER,
    FATHER,
    GUARDIAN,
    OTHER
}
