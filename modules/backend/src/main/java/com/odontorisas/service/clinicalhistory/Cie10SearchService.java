package com.odontorisas.service.clinicalhistory;

import com.odontorisas.persistence.entity.Cie10Code;
import com.odontorisas.persistence.repository.Cie10CodeRepository;
import com.odontorisas.service.clinicalhistory.Cie10SearchResult.Cie10PathSegment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Búsqueda del catálogo CIE10 respetando la jerarquía (spec: búsqueda jerárquica, design.md D6).
 * Sin aislamiento por tenant: el catálogo es global.
 */
@Service
public class Cie10SearchService {

    private final Cie10CodeRepository repository;

    public Cie10SearchService(Cie10CodeRepository repository) {
        this.repository = repository;
    }

    /** Busca por texto en la descripción, en cualquier nivel; cada resultado trae su ruta completa. */
    public List<Cie10SearchResult> search(String query) {
        return repository.findByDescripcionContainingIgnoreCase(query).stream()
            .map(this::toResult)
            .toList();
    }

    /** {@code true} si el código existe en el catálogo (validación de diagnóstico, D5). */
    public boolean exists(String code) {
        return repository.existsById(code);
    }

    private Cie10SearchResult toResult(Cie10Code code) {
        return new Cie10SearchResult(code.getCode(), code.getDescripcion(), code.getNivel().name(), pathTo(code));
    }

    /** Ruta raíz→nodo (capítulo primero) siguiendo `parent_code` hacia arriba. */
    private List<Cie10PathSegment> pathTo(Cie10Code code) {
        List<Cie10PathSegment> reversed = new ArrayList<>();
        Cie10Code current = code;
        while (current != null) {
            reversed.add(new Cie10PathSegment(current.getCode(), current.getDescripcion(), current.getNivel().name()));
            current = current.getParentCode() == null ? null : repository.findById(current.getParentCode()).orElse(null);
        }
        return reversed.reversed();
    }
}
