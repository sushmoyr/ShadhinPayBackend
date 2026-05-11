package com.shadhinpay.identity.dto;

import com.shadhinpay.identity.enums.IdentifierType;
import com.shadhinpay.identity.enums.OnboardingStatus;
import com.shadhinpay.identity.enums.UserStatus;
import java.util.UUID;

public record MerchantOnboardingDto(
    UUID userId,
    String identifier,
    IdentifierType identifierType,
    UserStatus status,
    String fullName,
    OnboardingStatus onboardingStatus) {}
