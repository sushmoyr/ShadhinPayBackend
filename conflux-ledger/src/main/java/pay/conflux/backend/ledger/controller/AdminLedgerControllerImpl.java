package pay.conflux.backend.ledger.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.common.dto.PaginationRequest;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.dto.BalanceDto;
import pay.conflux.backend.ledger.dto.JournalEntryDto;
import pay.conflux.backend.ledger.dto.TrialBalanceReportDto;
import pay.conflux.backend.ledger.usecase.GetAccountBalanceUseCase;
import pay.conflux.backend.ledger.usecase.internal.ListJournalEntriesUseCase;
import pay.conflux.backend.ledger.usecase.internal.VerifyTrialBalanceUseCase;

@RestController
@RequiredArgsConstructor
public class AdminLedgerControllerImpl implements AdminLedgerController {

  // Account-type label for chart-of-accounts codes, kept out of the entity to honour the
  // controllers-must-not-use-entities architectural rule. System codes match V1003 seed types.
  private static final Map<String, String> SYSTEM_ACCOUNT_TYPES =
      Map.of(
          "ESCROW", "CLEARING",
          "PLATFORM_REVENUE", "REVENUE",
          "VENDOR_PAYABLE", "LIABILITY",
          "MERCHANT_PAYABLE", "LIABILITY");

  private final ListJournalEntriesUseCase listJournalEntriesUseCase;
  private final VerifyTrialBalanceUseCase verifyTrialBalanceUseCase;
  private final GetAccountBalanceUseCase getAccountBalanceUseCase;

  @Override
  @PreAuthorize("hasAuthority('ADMIN_VIEWER')")
  public ResponseEntity<ApiResult<List<JournalEntryDto>>> listJournal(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "occurredAt") String sortBy,
      @RequestParam(defaultValue = "DESC") Sort.Direction order,
      @RequestParam(defaultValue = "true") boolean paginate,
      @RequestParam(required = false) String sourceType,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant startDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant endDate,
      @RequestParam(required = false) UUID ownerId) {

    PaginationRequest pagination = new PaginationRequest(page, size, sortBy, order, paginate);
    Page<JournalEntryDto> result =
        listJournalEntriesUseCase.execute(pagination, sourceType, startDate, endDate, ownerId);
    return ApiResult.ok(result);
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_VIEWER')")
  public ResponseEntity<ApiResult<BalanceDto>> getBalance(
      @PathVariable String code, @RequestParam(defaultValue = "BDT") String currency) {
    Money balance = getAccountBalanceUseCase.execute(null, code);
    String accountType = SYSTEM_ACCOUNT_TYPES.getOrDefault(code, "UNKNOWN");
    return ApiResult.ok(
        new BalanceDto(null, code, accountType, currency, balance.amount().toPlainString()));
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<TrialBalanceReportDto>> getTrialBalance() {
    return ApiResult.ok(verifyTrialBalanceUseCase.execute());
  }
}
