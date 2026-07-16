package com.odontorisas.common;

import java.time.LocalDate;
import java.time.Period;

/**
 * Derivación de la edad a partir de la fecha de nacimiento. Vive en {@code common} para que la
 * usen tanto la entidad ({@code persistence}) como la validación del request ({@code presentation})
 * sin duplicar la regla ni cruzar capas (presentation no puede depender de persistence).
 *
 * <p>La minoría de edad se <b>deriva</b>, nunca se declara (design.md D2): se recalcula en cada
 * lectura, así que jamás se desincroniza el día del cumpleaños.
 */
public final class Ages {

    /** Mayoría de edad: a partir de esta edad (inclusive) la persona es adulta. */
    public static final int ADULT_AGE = 18;

    private Ages() {
    }

    /** {@code true} si la fecha de nacimiento indica menor de edad. {@code null} → {@code false}. */
    public static boolean isMinor(LocalDate birthDate) {
        return birthDate != null && Period.between(birthDate, LocalDate.now()).getYears() < ADULT_AGE;
    }
}
