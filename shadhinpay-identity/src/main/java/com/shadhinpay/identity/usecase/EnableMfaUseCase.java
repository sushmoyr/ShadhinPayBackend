package com.shadhinpay.identity.usecase;

import com.shadhinpay.identity.dto.MfaEnableResponse;
import java.util.UUID;

public interface EnableMfaUseCase {
  MfaEnableResponse execute(UUID userId);
}
