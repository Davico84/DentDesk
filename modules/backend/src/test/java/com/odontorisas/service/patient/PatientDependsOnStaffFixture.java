package com.odontorisas.service.patient;

import com.odontorisas.service.staff.StaffService;

/**
 * Fixture de {@code ServiceDomainBoundariesTest}: {@code service.patient} depende de
 * {@code service.staff}, una arista que el mapa no declara. Vive en {@code src/test}; sin estereotipos
 * Spring ni lógica.
 */
@SuppressWarnings("unused")
class PatientDependsOnStaffFixture {
    private StaffService staff;
}
