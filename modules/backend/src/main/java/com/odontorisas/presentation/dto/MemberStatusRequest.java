package com.odontorisas.presentation.dto;

import com.odontorisas.common.MembershipStatus;
import jakarta.validation.constraints.NotNull;

public record MemberStatusRequest(@NotNull MembershipStatus status) {}
