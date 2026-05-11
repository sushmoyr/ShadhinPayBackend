package com.shadhinpay.risk.usecase.internal;

import com.shadhinpay.risk.dto.BlacklistEntryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListBlacklistUseCase {
  Page<BlacklistEntryDto> execute(Pageable pageable);
}
