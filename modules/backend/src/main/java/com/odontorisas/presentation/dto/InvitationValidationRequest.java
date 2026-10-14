package com.odontorisas.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record InvitationValidationRequest(@NotBlank String token) {}
