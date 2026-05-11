package pay.conflux.backend.risk.dto;

import jakarta.validation.constraints.NotNull;
import pay.conflux.backend.risk.entity.TrustLevel;

public record UpsertMerchantRiskProfileRequest(
    @NotNull TrustLevel trustLevel, String customLimits) {}
