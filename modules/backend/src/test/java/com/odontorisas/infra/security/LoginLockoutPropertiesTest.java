package com.odontorisas.infra.security;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Config del bloqueo por intentos fallidos (design.md D6, update-authentication): valores
 * inválidos deben ser detectables antes de que el sistema arranque. {@code maxAttempts < 1} lo
 * detecta Bean Validation ({@code @Min}, disparado por {@code @Validated} al hacer el binding
 * real); una {@code window} no positiva la rechaza el propio constructor compacto del record.
 */
class LoginLockoutPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void valid_values_are_accepted() {
        LoginLockoutProperties props = new LoginLockoutProperties(5, Duration.ofMinutes(15));

        assertThat(validator.validate(props)).isEmpty();
        assertThat(props.maxAttempts()).isEqualTo(5);
        assertThat(props.window()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void max_attempts_below_one_fails_validation() {
        LoginLockoutProperties props = new LoginLockoutProperties(0, Duration.ofMinutes(15));

        Set<ConstraintViolation<LoginLockoutProperties>> violations = validator.validate(props);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void zero_or_negative_window_is_rejected_at_construction() {
        assertThatThrownBy(() -> new LoginLockoutProperties(5, Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoginLockoutProperties(5, Duration.ofMinutes(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void null_window_is_rejected_at_construction() {
        assertThatThrownBy(() -> new LoginLockoutProperties(5, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
