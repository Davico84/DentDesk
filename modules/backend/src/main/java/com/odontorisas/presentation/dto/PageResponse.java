package com.odontorisas.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Envoltorio de paginación con forma <b>estable</b> para el contrato. Se usa en vez de devolver
 * el {@code Page} de Spring directamente, cuya serialización JSON es inestable (Spring avisa de
 * ello) y no debe filtrarse a un contrato del que el frontend genera cliente.
 *
 * @param content       la página de elementos
 * @param page          índice de página (base 0)
 * @param size          tamaño de página solicitado
 * @param totalElements cuántos hay en total (todas las páginas)
 * @param totalPages    número total de páginas
 * @param last          si es la última página
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    @Schema(description = "Cuántos elementos hay en total, en todas las páginas.")
    long totalElements,
    int totalPages,
    boolean last) {

    /** Construye la respuesta desde un {@link Page} ya mapeado a los tipos de salida. */
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
            page.getContent(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
