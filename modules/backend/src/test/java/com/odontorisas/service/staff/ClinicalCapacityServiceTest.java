package com.odontorisas.service.staff;

import com.odontorisas.common.ClinicalCapacityState;
import com.odontorisas.common.MembershipStatus;
import com.odontorisas.common.Role;
import com.odontorisas.persistence.entity.Membership;
import com.odontorisas.persistence.entity.ProfessionalCredential;
import com.odontorisas.persistence.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regla de la capacidad clínica efectiva ({@code update-staff-professional-credential} design.md D3), sin
 * base: la vigencia, su orden de motivos y la zona de Perú. Scenarios de {@code staff}: <i>La habilitación
 * vence sola</i>, <i>Último día de habilitación</i>, <i>Clínicas con otra zona horaria</i>, <i>Retirada en
 * una clínica, vigente en otra</i> y <i>Miembro desactivado con la sesión todavía abierta</i>.
 */
class ClinicalCapacityServiceTest {

    private static final LocalDate LAST_DAY = LocalDate.of(2026, 10, 26);

    private static Membership membership(MembershipStatus status, boolean revoked) {
        return Membership.builder()
            .user(User.builder().id(7L).build())
            .role(Role.MEMBER)
            .status(status)
            .clinicalCapacityRevokedAt(revoked ? Instant.parse("2026-10-01T12:00:00Z") : null)
            .clinicalCapacityRevokedBy(revoked ? 1L : null)
            .build();
    }

    private static ProfessionalCredential credential() {
        return new ProfessionalCredential(7L, "12345", LAST_DAY, Instant.parse("2026-01-01T12:00:00Z"));
    }

    private static ClinicalCapacityState evaluate(Membership m, ProfessionalCredential c, LocalDate today) {
        return ClinicalCapacityService.evaluate(m, c, today).state();
    }

    /** Hoy en Perú para un instante dado, como lo calcula el servicio. */
    private static LocalDate todayInPeruAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new ClinicalCapacityService(null, null, null, clock).todayInPeru();
    }

    @Test
    void enabled_with_an_active_membership_no_revocation_and_a_current_credential() {
        assertThat(evaluate(membership(MembershipStatus.ACTIVE, false), credential(), LAST_DAY.minusDays(3)))
            .isEqualTo(ClinicalCapacityState.ENABLED);
    }

    @Test
    void the_last_day_is_still_valid_and_the_next_one_has_expired() {
        Membership active = membership(MembershipStatus.ACTIVE, false);
        assertThat(evaluate(active, credential(), LAST_DAY)).isEqualTo(ClinicalCapacityState.ENABLED);
        assertThat(evaluate(active, credential(), LAST_DAY.plusDays(1))).isEqualTo(ClinicalCapacityState.EXPIRED);
    }

    @Test
    void without_a_credential_there_is_no_capacity() {
        assertThat(evaluate(membership(MembershipStatus.ACTIVE, false), null, LAST_DAY))
            .isEqualTo(ClinicalCapacityState.NO_CREDENTIAL);
    }

    @Test
    void the_reasons_follow_the_declared_order() {
        // Inactiva gana a todo; retirada gana a la credencial vencida o ausente.
        assertThat(evaluate(membership(MembershipStatus.INACTIVE, true), null, LAST_DAY))
            .isEqualTo(ClinicalCapacityState.INACTIVE_MEMBERSHIP);
        assertThat(evaluate(membership(MembershipStatus.ACTIVE, true), credential(), LAST_DAY.plusDays(5)))
            .isEqualTo(ClinicalCapacityState.REVOKED);
    }

    @Test
    void a_revocation_in_one_clinic_does_not_affect_another() {
        ProfessionalCredential same = credential();
        assertThat(evaluate(membership(MembershipStatus.ACTIVE, true), same, LAST_DAY))
            .isEqualTo(ClinicalCapacityState.REVOKED);
        assertThat(evaluate(membership(MembershipStatus.ACTIVE, false), same, LAST_DAY))
            .isEqualTo(ClinicalCapacityState.ENABLED);
    }

    @Test
    void a_deactivated_member_has_no_capacity_even_with_a_current_credential() {
        assertThat(evaluate(membership(MembershipStatus.INACTIVE, false), credential(), LAST_DAY))
            .isEqualTo(ClinicalCapacityState.INACTIVE_MEMBERSHIP);
    }

    @Test
    void the_validity_day_is_the_peruvian_one_whatever_the_server_zone() {
        // 04:30 UTC del 27 = 23:30 del 26 en Lima: todavía es el último día.
        assertThat(todayInPeruAt("2026-10-27T04:30:00Z")).isEqualTo(LAST_DAY);
        // 05:30 UTC del 27 = 00:30 del 27 en Lima: ya venció.
        assertThat(todayInPeruAt("2026-10-27T05:30:00Z")).isEqualTo(LAST_DAY.plusDays(1));
    }

    @Test
    void two_clinics_in_different_zones_see_the_same_expiry_at_the_same_instant() {
        // La zona de la clínica no interviene: en el mismo instante, una clínica en Madrid (ya 27) y otra en
        // Lima (todavía 26) evalúan la misma fecha de Perú, así que la credencial vale igual en las dos.
        LocalDate today = todayInPeruAt("2026-10-27T04:30:00Z");
        Membership madrid = membership(MembershipStatus.ACTIVE, false);
        Membership lima = membership(MembershipStatus.ACTIVE, false);
        assertThat(evaluate(madrid, credential(), today)).isEqualTo(evaluate(lima, credential(), today))
            .isEqualTo(ClinicalCapacityState.ENABLED);
    }
}
