package pay.conflux.backend.risk.usecase.impl;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.error.ResourceNotFoundException;
import pay.conflux.backend.risk.entity.BlacklistEntry;
import pay.conflux.backend.risk.events.BlacklistEntryChangedEvent;
import pay.conflux.backend.risk.repository.BlacklistEntryRepository;
import pay.conflux.backend.risk.usecase.internal.RemoveBlacklistEntryUseCase;

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
