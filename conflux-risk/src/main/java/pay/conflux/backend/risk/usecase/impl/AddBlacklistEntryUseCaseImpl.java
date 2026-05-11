package pay.conflux.backend.risk.usecase.impl;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.DuplicateResourceException;
import pay.conflux.backend.risk.dto.AddBlacklistEntryRequest;
import pay.conflux.backend.risk.dto.BlacklistEntryDto;
import pay.conflux.backend.risk.entity.BlacklistEntry;
import pay.conflux.backend.risk.events.BlacklistEntryChangedEvent;
import pay.conflux.backend.risk.mapper.BlacklistEntryMapper;
import pay.conflux.backend.risk.repository.BlacklistEntryRepository;
import pay.conflux.backend.risk.usecase.internal.AddBlacklistEntryUseCase;

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
