package com.odontorisas.common;

/**
 * Capacidad clínica de un usuario en la organización activa ({@code update-staff-professional-credential}
 * design.md D3). Se evalúa en el orden declarado: la primera condición que falla es el motivo.
 */
public enum ClinicalCapacityState {
    /** La membresía en la clínica activa no está {@link MembershipStatus#ACTIVE}. */
    INACTIVE_MEMBERSHIP,
    /** El administrador de la clínica retiró la capacidad clínica en esa membresía. */
    REVOKED,
    /** El usuario no declaró su credencial profesional. */
    NO_CREDENTIAL,
    /** La fecha "habilitado hasta" ya pasó en la hora de Perú. */
    EXPIRED,
    /** Puede firmar actos clínicos. */
    ENABLED
}
