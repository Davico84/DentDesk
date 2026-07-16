package com.odontorisas.service.patient;

/**
 * Criterios de búsqueda del listado de pacientes. {@code query} busca por <b>nombre o
 * documento</b> (parcial, insensible a mayúsculas) — el uso diario de recepción.
 * {@code includeInactive} incluye a los dados de baja (por defecto solo activos), lo que
 * permite encontrarlos para reactivarlos (design.md D4).
 */
public record PatientSearch(String query, boolean includeInactive) {
}
