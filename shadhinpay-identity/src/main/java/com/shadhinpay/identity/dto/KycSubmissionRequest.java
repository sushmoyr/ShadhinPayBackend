package com.shadhinpay.identity.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record KycSubmissionRequest(
    @NotBlank(message = "NID front URL is required") @URL(message = "Must be a valid URL")
        String nidFrontUrl,
    @NotBlank(message = "NID back URL is required") @URL(message = "Must be a valid URL")
        String nidBackUrl,
    @NotBlank(message = "Trade license URL is required") @URL(message = "Must be a valid URL")
        String tradeLicenseUrl,
    @URL(message = "Must be a valid URL") String tinUrl) {}
