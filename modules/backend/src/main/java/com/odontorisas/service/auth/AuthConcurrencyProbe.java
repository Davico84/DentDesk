package com.odontorisas.service.auth;

/**
 * Puntos de intercalado de {@link AuthService} para los tests de concurrencia
 * ({@code update-authentication-my-profile} design.md D3): un test los usa para detener un hilo justo en la
 * ventana de una carrera y probar que el bloqueo de la fila la cierra, sin depender del azar del timing.
 *
 * <p>En producción no hay ningún bean de este tipo y {@link AuthService} usa {@link #NONE}: los métodos no
 * hacen nada. Solo un {@code @TestConfiguration} registra uno.
 */
public interface AuthConcurrencyProbe {

    AuthConcurrencyProbe NONE = new AuthConcurrencyProbe() {
    };

    /** Login: la contraseña ya se verificó con BCrypt y todavía no se tomó el bloqueo de la fila. */
    default void afterLoginPasswordVerified() {
    }

    /** Renovación: el token ya se consumió y todavía no se insertó el nuevo. */
    default void afterRefreshTokenConsumed() {
    }

    /** Cambio de contraseña: la actual ya se verificó y todavía no se tomó el {@code FOR UPDATE}. */
    default void afterChangePasswordVerified() {
    }

    /** Cambio de contraseña: el {@code FOR UPDATE} ya está tomado y todavía no se cambió nada. */
    default void afterChangePasswordLocked() {
    }
}
