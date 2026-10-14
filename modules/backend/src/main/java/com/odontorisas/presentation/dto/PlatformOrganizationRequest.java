package com.odontorisas.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El largo máximo del nombre es el de {@code organizations.name} (VARCHAR(120)). */
public record PlatformOrganizationRequest(
    @NotBlank @Size(min = 2, max = 120) String organizationName,
    @NotBlank @Email String adminEmail) {}
