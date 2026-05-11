package com.shadhinpay.identity.usecase;

import java.util.UUID;

public interface UnblockUserUseCase {
  void execute(UUID userId);
}
