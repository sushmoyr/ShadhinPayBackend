package pay.conflux.backend.ledger.usecase.internal;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import pay.conflux.backend.common.annotation.UseCase;
import pay.conflux.backend.common.dto.PaginationRequest;
import pay.conflux.backend.ledger.dto.JournalEntryDto;
import pay.conflux.backend.ledger.entity.JournalEntry;
import pay.conflux.backend.ledger.mapper.LedgerMapper;
import pay.conflux.backend.ledger.repository.JournalEntryRepository;
import pay.conflux.backend.ledger.spec.JournalEntrySpec;

@UseCase
@RequiredArgsConstructor
public class ListJournalEntriesUseCaseImpl implements ListJournalEntriesUseCase {

  private final JournalEntryRepository journalEntryRepository;
  private final LedgerMapper mapper;

  @Override
  @Transactional(readOnly = true)
  public Page<JournalEntryDto> execute(
      PaginationRequest pagination,
      String sourceType,
      Instant startDate,
      Instant endDate,
      UUID ownerId) {

    Specification<JournalEntry> spec =
        JournalEntrySpec.filterBy(sourceType, startDate, endDate, ownerId);

    Pageable pageable = pagination.toPageable();
    Page<JournalEntry> page = journalEntryRepository.findAll(spec, pageable);

    return page.map(mapper::toDto);
  }
}
