package com.shadhinpay.risk.usecase.internal;

import com.shadhinpay.risk.dto.AddBlacklistEntryRequest;
import com.shadhinpay.risk.dto.BlacklistEntryDto;

public interface AddBlacklistEntryUseCase {
  BlacklistEntryDto execute(AddBlacklistEntryRequest request);
}
