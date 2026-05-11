package com.shadhinpay.ledger.usecase.internal;

import com.shadhinpay.common.annotation.UseCase;
import com.shadhinpay.common.dto.PaginationRequest;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.entity.JournalEntry;
import com.shadhinpay.ledger.mapper.LedgerMapper;
import com.shadhinpay.ledger.repository.JournalEntryRepository;
import com.shadhinpay.ledger.spec.JournalEntrySpec;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

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
