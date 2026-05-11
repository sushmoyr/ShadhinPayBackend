package pay.conflux.backend.quota.controller.dto;

import java.util.UUID;

public record AdminQuotaUsageDto(
    UUID merchantId, int usedCount, int freeRemaining, String period) {}
