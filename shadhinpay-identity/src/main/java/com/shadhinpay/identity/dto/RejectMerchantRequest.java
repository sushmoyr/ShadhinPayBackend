package com.shadhinpay.identity.dto;

import com.shadhinpay.common.validator.SafeString;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectMerchantRequest(
    @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        @SafeString
        String reason) {}
