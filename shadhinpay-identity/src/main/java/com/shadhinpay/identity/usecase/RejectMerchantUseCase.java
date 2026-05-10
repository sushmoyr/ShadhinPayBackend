package com.shadhinpay.identity.usecase;

import com.shadhinpay.identity.dto.RejectMerchantRequest;
import java.util.UUID;

public interface RejectMerchantUseCase {
  void execute(UUID merchantProfileId, RejectMerchantRequest request);
}
