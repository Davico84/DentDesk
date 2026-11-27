package com.odontorisas.service.staff;

import com.odontorisas.service.auth.AuthService;

/**
 * Fixture de {@code ServiceDomainBoundariesTest}: una clase de {@code service.staff} que depende de
 * {@code service.auth}. Junto con la dependencia real {@code AuthService → StaffService} forma el ciclo
 * {@code auth ↔ staff} que la regla debe rechazar. Vive en {@code src/test}: el importador de
 * producción ({@code DO_NOT_INCLUDE_TESTS}) no la ve. Sin estereotipos Spring ni lógica.
 */
@SuppressWarnings("unused")
class StaffDependsOnAuthFixture {
    private AuthService auth;
}
