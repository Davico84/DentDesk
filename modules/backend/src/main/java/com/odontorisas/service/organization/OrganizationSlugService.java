package com.odontorisas.service.organization;

import com.odontorisas.persistence.repository.OrganizationRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Deriva un {@code slug} único y legible a partir del nombre de una organización.
 *
 * <p>La unicidad definitiva la garantiza la restricción {@code UNIQUE} de la base de
 * datos; este servicio hace la desambiguación de mejor esfuerzo (una carrera entre dos
 * registros concurrentes se traduce luego a un {@code 409} — ver el manejo de
 * {@code DataIntegrityViolationException} en el registro).
 */
@Service
public class OrganizationSlugService {

    private static final int MAX_LENGTH = 140;
    private static final String FALLBACK = "org";

    private final OrganizationRepository organizations;

    public OrganizationSlugService(OrganizationRepository organizations) {
        this.organizations = organizations;
    }

    /** Deriva un slug disponible a partir del nombre, desambiguando si ya existe. */
    public String slugFor(String name) {
        String base = baseSlug(name);
        if (!organizations.existsBySlug(base)) {
            return base;
        }
        for (int suffix = 2; ; suffix++) {
            String candidate = truncate(base, suffix) + "-" + suffix;
            if (!organizations.existsBySlug(candidate)) {
                return candidate;
            }
        }
    }

    /** Normaliza a minúsculas ASCII con guiones (sin acentos ni símbolos). */
    private String baseSlug(String name) {
        String normalized = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")                 // quita marcas de acento
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")             // no alfanumérico -> guion
                .replaceAll("(^-+)|(-+$)", "");            // recorta guiones extremos
        if (normalized.length() > MAX_LENGTH) {
            normalized = normalized.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return normalized.isEmpty() ? FALLBACK : normalized;
    }

    /** Deja sitio para el sufijo "-N" sin exceder el largo máximo de columna. */
    private String truncate(String base, int suffix) {
        int room = MAX_LENGTH - ("-" + suffix).length();
        return base.length() <= room ? base : base.substring(0, room).replaceAll("-+$", "");
    }
}
