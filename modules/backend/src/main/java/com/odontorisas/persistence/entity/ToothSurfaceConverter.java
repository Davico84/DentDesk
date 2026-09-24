package com.odontorisas.persistence.entity;

import com.odontorisas.common.ToothSurface;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;

/**
 * Persiste {@link ToothSurface} como el código de una letra ({@code M/D/V/P/L/O/I}), que es el
 * conjunto cerrado que declara el {@code CHECK} de la columna {@code surface} en
 * {@code dental_chart_finding} (V18) — no el nombre completo del enum de Java.
 */
@Converter(autoApply = true)
public class ToothSurfaceConverter implements AttributeConverter<ToothSurface, String> {

    @Override
    public String convertToDatabaseColumn(ToothSurface attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public ToothSurface convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return Arrays.stream(ToothSurface.values())
            .filter(surface -> surface.code().equals(dbData))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Código de superficie desconocido: " + dbData));
    }
}
