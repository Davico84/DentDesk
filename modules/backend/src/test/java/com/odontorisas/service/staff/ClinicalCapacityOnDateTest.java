package com.odontorisas.service.staff;

import com.odontorisas.common.ClinicalCapacityState;
import com.odontorisas.common.OrganizationContext;
import com.odontorisas.persistence.repository.MembershipRepository;
import com.odontorisas.persistence.repository.MembershipRepository.CapacityFacts;
import com.odontorisas.service.staff.ClinicalCapacityService.CapacityView;
import com.odontorisas.service.staff.ClinicalCapacityService.EnabledMember;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Capacidad del odontólogo en la fecha de la cita (add-appointments design.md D4, tarea 3.2): la vigencia se
 * mide en el día de la cita, no hoy; y la lista de agendables, hoy.
 */
class ClinicalCapacityOnDateTest {

    private static final long ORG = 10L;
    private static final long DENTIST = 7L;
    private static final LocalDate LICENSED_UNTIL = LocalDate.of(2030, 11, 30);

    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2030-11-20T15:00:00Z"), ClinicalCapacityService.PERU);
    private final ClinicalCapacityService service = new ClinicalCapacityService(memberships, null, null, clock);

    private record Facts(Long getUserId, String getFullName, String getMembershipStatus, Boolean getCapacityRevoked,
                         LocalDate getLicensedUntil) implements CapacityFacts {
    }

    @BeforeEach
    void activeClinic() {
        OrganizationContext.set(ORG);
    }

    @AfterEach
    void clear() {
        OrganizationContext.clear();
    }

    private void given(Facts... facts) {
        when(memberships.capacityFacts(DENTIST)).thenReturn(List.of(facts));
    }

    private ClinicalCapacityState stateOn(LocalDate date) {
        return service.onDate(DENTIST, date).map(CapacityView::state).orElseThrow();
    }

    @Test
    void a_current_credential_on_the_appointment_date_is_enabled() {
        given(new Facts(DENTIST, "Dra. Ríos", "ACTIVE", false, LICENSED_UNTIL));

        assertThat(stateOn(LICENSED_UNTIL)).isEqualTo(ClinicalCapacityState.ENABLED);
    }

    @Test
    void a_credential_expiring_before_the_appointment_is_not_enabled_on_that_date() {
        given(new Facts(DENTIST, "Dra. Ríos", "ACTIVE", false, LICENSED_UNTIL));

        assertThat(stateOn(LocalDate.of(2030, 12, 2))).isEqualTo(ClinicalCapacityState.EXPIRED);
    }

    @Test
    void without_a_credential_there_is_no_capacity() {
        given(new Facts(DENTIST, "Dra. Ríos", "ACTIVE", false, null));

        assertThat(stateOn(LICENSED_UNTIL)).isEqualTo(ClinicalCapacityState.NO_CREDENTIAL);
    }

    @Test
    void a_revoked_capacity_is_not_enabled() {
        given(new Facts(DENTIST, "Dra. Ríos", "ACTIVE", true, LICENSED_UNTIL));

        assertThat(stateOn(LICENSED_UNTIL)).isEqualTo(ClinicalCapacityState.REVOKED);
    }

    @Test
    void an_inactive_membership_is_not_enabled() {
        given(new Facts(DENTIST, "Dra. Ríos", "INACTIVE", false, LICENSED_UNTIL));

        assertThat(stateOn(LICENSED_UNTIL)).isEqualTo(ClinicalCapacityState.INACTIVE_MEMBERSHIP);
    }

    @Test
    void a_user_who_is_not_a_member_has_no_capacity_view() {
        given();

        assertThat(service.onDate(DENTIST, LICENSED_UNTIL)).isEmpty();
    }

    @Test
    void the_schedulable_list_has_only_members_enabled_today_sorted_by_name() {
        when(memberships.capacityFacts(isNull())).thenReturn(List.of(
            new Facts(DENTIST, "Zoe", "ACTIVE", false, LICENSED_UNTIL),
            new Facts(8L, "Ana", "ACTIVE", false, LICENSED_UNTIL),
            new Facts(9L, "Beto", "ACTIVE", false, null),
            new Facts(10L, "Carla", "ACTIVE", false, LocalDate.of(2030, 11, 19))));

        assertThat(service.enabledMembersToday()).extracting(EnabledMember::fullName).containsExactly("Ana", "Zoe");
    }
}
