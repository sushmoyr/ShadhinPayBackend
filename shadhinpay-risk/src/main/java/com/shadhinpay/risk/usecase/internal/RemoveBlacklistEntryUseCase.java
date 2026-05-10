package com.shadhinpay.risk.usecase.internal;

import java.util.UUID;

public interface RemoveBlacklistEntryUseCase {
  void execute(UUID id);
}
