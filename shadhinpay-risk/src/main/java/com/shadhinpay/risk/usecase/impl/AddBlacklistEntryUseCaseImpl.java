package com.shadhinpay.risk.usecase.impl;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.error.DuplicateResourceException;
import com.shadhinpay.risk.dto.AddBlacklistEntryRequest;
import com.shadhinpay.risk.dto.BlacklistEntryDto;
import com.shadhinpay.risk.entity.BlacklistEntry;
import com.shadhinpay.risk.events.BlacklistEntryChangedEvent;
import com.shadhinpay.risk.mapper.BlacklistEntryMapper;
import com.shadhinpay.risk.repository.BlacklistEntryRepository;
import com.shadhinpay.risk.usecase.internal.AddBlacklistEntryUseCase;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@RequiredArgsConstructor
public class AddBlacklistEntryUseCaseImpl implements AddBlacklistEntryUseCase {

  private final BlacklistEntryRepository blacklistEntryRepository;
  private final BlacklistEntryMapper blacklistEntryMapper;
  private final ApplicationEventPublisher eventPublisher;

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

    eventPublisher.publishEvent(
        new BlacklistEntryChangedEvent(
            saved.getType(), saved.getValue(), BlacklistEntryChangedEvent.ChangeKind.ADDED));

    return blacklistEntryMapper.toDto(saved);
  }
}
