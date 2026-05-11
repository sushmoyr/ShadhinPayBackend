package pay.conflux.backend.risk.dto;

import java.util.UUID;
import pay.conflux.backend.risk.entity.TrustLevel;

public record MerchantRiskProfileDto(UUID merchantId, TrustLevel trustLevel, String customLimits) {}
