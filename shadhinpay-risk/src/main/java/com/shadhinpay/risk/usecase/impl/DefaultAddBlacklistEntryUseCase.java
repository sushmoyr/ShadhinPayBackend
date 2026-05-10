package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.error.DuplicateResourceException;
import com.shadhinpay.risk.dto.AddBlacklistEntryRequest;
import com.shadhinpay.risk.dto.BlacklistEntryDto;
import com.shadhinpay.risk.engine.BlacklistCache;
import com.shadhinpay.risk.entity.BlacklistEntry;
import com.shadhinpay.risk.mapper.BlacklistEntryMapper;
import com.shadhinpay.risk.repository.BlacklistEntryRepository;
import com.shadhinpay.risk.usecase.internal.AddBlacklistEntryUseCase;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultAddBlacklistEntryUseCase implements AddBlacklistEntryUseCase {

  private final BlacklistEntryRepository blacklistEntryRepository;
  private final BlacklistEntryMapper blacklistEntryMapper;
  private final BlacklistCache blacklistCache;

  public DefaultAddBlacklistEntryUseCase(
      BlacklistEntryRepository blacklistEntryRepository,
      BlacklistEntryMapper blacklistEntryMapper,
      BlacklistCache blacklistCache) {
    this.blacklistEntryRepository = blacklistEntryRepository;
    this.blacklistEntryMapper = blacklistEntryMapper;
    this.blacklistCache = blacklistCache;
  }

  @Override
  @Transactional
  public BlacklistEntryDto execute(AddBlacklistEntryRequest request) {
    blacklistEntryRepository
        .findActiveByTypeAndValue(request.type(), request.value(), Instant.now())
        .ifPresent(
            b -> {
              throw new DuplicateResourceException(
                  "BlacklistEntry", "type and value", request.type() + "-" + request.value());
            });

    BlacklistEntry entry = new BlacklistEntry();
    entry.setType(request.type());
    entry.setValue(request.value());
    entry.setReason(request.reason());
    entry.setExpiresAt(request.expiresAt());

    BlacklistEntry saved = blacklistEntryRepository.save(entry);
    blacklistCache.add(saved.getType(), saved.getValue());

    return blacklistEntryMapper.toDto(saved);
  }
}
