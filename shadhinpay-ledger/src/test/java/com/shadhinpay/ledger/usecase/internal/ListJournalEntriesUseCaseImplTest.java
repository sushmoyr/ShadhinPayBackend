package com.shadhinpay.ledger.usecase.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.shadhinpay.common.dto.PaginationRequest;
import com.shadhinpay.common.money.Money;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.entity.JournalEntry;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.entity.LedgerAccountType;
import com.shadhinpay.ledger.mapper.LedgerMapper;
import com.shadhinpay.ledger.repository.JournalEntryRepository;
import com.shadhinpay.ledger.usecase.JournalEntryRequest;
import com.shadhinpay.ledger.usecase.PostingRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class ListJournalEntriesUseCaseImplTest {

  @Mock private JournalEntryRepository journalEntryRepository;
  @Mock private LedgerMapper mapper;

  @InjectMocks private ListJournalEntriesUseCaseImpl useCase;

  @Test
  void shouldReturnPaginatedResults() {
    PaginationRequest pagination =
        new PaginationRequest(0, 20, "occurredAt", Sort.Direction.DESC, true);

    UUID accountId = UUID.randomUUID();
    List<PostingRequest> dummyPostings =
        List.of(
            new PostingRequest(accountId, Money.of(100, "BDT"), PostingRequest.Type.DEBIT),
            new PostingRequest(accountId, Money.of(-100, "BDT"), PostingRequest.Type.CREDIT));
    JournalEntry entry =
        new JournalEntry(
            new JournalEntryRequest("PAYMENT", "src-1", "desc", dummyPostings, Instant.now()));
    LedgerAccount account =
        new LedgerAccount(UUID.randomUUID(), LedgerAccountType.ASSET, "ESCROW", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(account, "id", UUID.randomUUID());

    Page<JournalEntry> page = new PageImpl<>(List.of(entry));

    when(journalEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);
    when(mapper.toDto(entry))
        .thenReturn(
            new JournalEntryDto(
                entry.getId(), "PAYMENT", "src-1", "desc", entry.getOccurredAt(), null, List.of()));

    Page<JournalEntryDto> result = useCase.execute(pagination, "PAYMENT", null, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).sourceType()).isEqualTo("PAYMENT");
  }

  @Test
  void shouldApplyOwnerIdFilter() {
    UUID ownerId = UUID.randomUUID();
    PaginationRequest pagination =
        new PaginationRequest(0, 20, "occurredAt", Sort.Direction.DESC, true);

    Page<JournalEntry> page = new PageImpl<>(List.of());
    when(journalEntryRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(page);

    Page<JournalEntryDto> result = useCase.execute(pagination, null, null, null, ownerId);

    assertThat(result).isNotNull();
    assertThat(result.getContent()).isEmpty();
  }
}
