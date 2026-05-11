package com.shadhinpay.ledger.usecase.internal;

import com.shadhinpay.common.dto.PaginationRequest;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;

/**
 * Lists journal entries with pagination and optional filters.
 *
 * <p><b>Internal to ledger.</b> Not exposed as a cross-module use-case contract.
 */
public interface ListJournalEntriesUseCase {

  Page<JournalEntryDto> execute(
      PaginationRequest pagination,
      String sourceType,
      Instant startDate,
      Instant endDate,
      UUID ownerId);
}
