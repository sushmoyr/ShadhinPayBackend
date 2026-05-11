package pay.conflux.backend.quota.controller.dto;

public record QuotaUsageDto(int usedCount, int freeRemaining, String period) {}
