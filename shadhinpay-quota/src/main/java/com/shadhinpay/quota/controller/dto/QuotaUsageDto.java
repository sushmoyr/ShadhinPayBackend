package com.shadhinpay.quota.controller.dto;

public record QuotaUsageDto(int usedCount, int freeRemaining, String period) {}
