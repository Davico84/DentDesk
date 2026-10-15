package com.odontorisas.service.staff;

import com.odontorisas.common.InvitationStatus;

import java.time.Instant;

/**
 * Estado de una invitación tal como lo ve el administrador. {@link #EXPIRED} no se persiste: es una invitación
 * {@code PENDING} cuya vigencia terminó (add-staff design D1).
 */
public enum InvitationState {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED;

    static InvitationState of(InvitationStatus status, Instant expiresAt, Instant now) {
        return switch (status) {
            case PENDING -> expiresAt.isAfter(now) ? PENDING : EXPIRED;
            case ACCEPTED -> ACCEPTED;
            case REVOKED -> REVOKED;
        };
    }
}
