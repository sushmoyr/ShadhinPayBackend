package com.shadhinpay.identity.usecase;

import com.shadhinpay.identity.dto.BlockUserRequest;
import java.util.UUID;

public interface BlockUserUseCase {
  void execute(UUID userId, BlockUserRequest request);
}
