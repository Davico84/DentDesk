package com.odontorisas.service.appointment;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;

/** Nombre del día en español para los mensajes de error. */
final class DayNames {

    private static final Locale SPANISH = Locale.forLanguageTag("es-PE");

    private DayNames() {
    }

    static String spanish(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, SPANISH);
    }
}
