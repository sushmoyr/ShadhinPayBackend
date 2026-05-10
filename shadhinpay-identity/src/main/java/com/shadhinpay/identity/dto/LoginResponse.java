package com.shadhinpay.identity.dto;

import com.shadhinpay.identity.entity.enums.UserType;
import java.util.UUID;

public record LoginResponse(String authToken, UUID userId, UserType userType) {}
