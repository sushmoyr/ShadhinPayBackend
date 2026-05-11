package com.shadhinpay.identity.dto;

import com.shadhinpay.common.validator.SafeString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterMerchantRequest(
    @NotBlank @Size(min = 3, max = 255) @SafeString String identifier,
    @NotBlank @Size(min = 8, max = 72) @SafeString String password,
    @NotBlank @Size(min = 2, max = 255) @SafeString String fullName) {}
