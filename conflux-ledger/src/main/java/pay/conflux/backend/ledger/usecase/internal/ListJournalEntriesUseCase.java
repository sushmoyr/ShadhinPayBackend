package pay.conflux.backend.ledger.usecase.internal;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import pay.conflux.backend.common.dto.PaginationRequest;
import pay.conflux.backend.ledger.dto.JournalEntryDto;

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
