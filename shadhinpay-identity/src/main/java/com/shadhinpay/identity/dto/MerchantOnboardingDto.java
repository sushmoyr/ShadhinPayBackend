package com.shadhinpay.identity.dto;

import com.shadhinpay.identity.entity.enums.IdentifierType;
import com.shadhinpay.identity.entity.enums.OnboardingStatus;
import com.shadhinpay.identity.entity.enums.UserStatus;
import java.util.UUID;

public record MerchantOnboardingDto(
    UUID userId,
    String identifier,
    IdentifierType identifierType,
    UserStatus status,
    String fullName,
    OnboardingStatus onboardingStatus) {}
