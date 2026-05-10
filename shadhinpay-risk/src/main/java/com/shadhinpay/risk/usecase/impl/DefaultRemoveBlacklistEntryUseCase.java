package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.engine.BlacklistCache;
import com.shadhinpay.risk.entity.BlacklistEntry;
import com.shadhinpay.risk.repository.BlacklistEntryRepository;
import com.shadhinpay.risk.usecase.internal.RemoveBlacklistEntryUseCase;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultRemoveBlacklistEntryUseCase implements RemoveBlacklistEntryUseCase {

  private final BlacklistEntryRepository blacklistEntryRepository;
  private final BlacklistCache blacklistCache;

  public DefaultRemoveBlacklistEntryUseCase(
      BlacklistEntryRepository blacklistEntryRepository, BlacklistCache blacklistCache) {
    this.blacklistEntryRepository = blacklistEntryRepository;
    this.blacklistCache = blacklistCache;
  }

  @Override
  @Transactional
  public void execute(UUID id) {
    BlacklistEntry entry =
        blacklistEntryRepository
            .findById(id)
            .filter(b -> !b.isDeleted())
            .orElseThrow(() -> new ResourceNotFoundException("BlacklistEntry", id));

    entry.setDeleted(true);
    blacklistEntryRepository.save(entry);
    blacklistCache.remove(entry.getType(), entry.getValue());
  }
}
