package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.risk.entity.BlacklistEntry;
import com.shadhinpay.risk.events.BlacklistEntryChangedEvent;
import com.shadhinpay.risk.repository.BlacklistEntryRepository;
import com.shadhinpay.risk.usecase.internal.RemoveBlacklistEntryUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class RemoveBlacklistEntryUseCaseImpl implements RemoveBlacklistEntryUseCase {

  private final BlacklistEntryRepository blacklistEntryRepository;
  private final ApplicationEventPublisher eventPublisher;

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

    eventPublisher.publishEvent(
        new BlacklistEntryChangedEvent(
            entry.getType(), entry.getValue(), BlacklistEntryChangedEvent.ChangeKind.REMOVED));
  }
}
