package com.shadhinpay.identity.dto;

import com.shadhinpay.common.validator.SafeString;
import jakarta.validation.constraints.NotBlank;

public record MfaDisableRequest(@NotBlank @SafeString String password) {}
