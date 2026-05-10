package com.shadhinpay.identity.usecase;

import java.util.UUID;

public interface VerifyMerchantUseCase {
  void execute(UUID merchantProfileId);
}
