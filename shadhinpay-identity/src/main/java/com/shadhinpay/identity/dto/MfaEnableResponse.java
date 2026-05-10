package com.shadhinpay.identity.dto;

public record MfaEnableResponse(String secret, String provisioningUri, String qrCodeBase64) {}
