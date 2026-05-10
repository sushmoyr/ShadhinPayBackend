package com.shadhinpay.ledger.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.common.dto.PaginationRequest;
import com.shadhinpay.common.error.ErrorCode;
import com.shadhinpay.ledger.dto.BalanceDto;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.dto.TrialBalanceReportDto;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.mapper.LedgerMapper;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.usecase.internal.ListJournalEntriesUseCase;
import com.shadhinpay.ledger.usecase.internal.VerifyTrialBalanceUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminLedgerControllerImpl implements AdminLedgerController {

  private final ListJournalEntriesUseCase listJournalEntriesUseCase;
  private final VerifyTrialBalanceUseCase verifyTrialBalanceUseCase;
  private final LedgerAccountRepository accountRepository;
  private final LedgerMapper mapper;

  @Override
  @PreAuthorize("hasAuthority('ADMIN_VIEWER')")
  public ResponseEntity<ApiResult<List<JournalEntryDto>>> listJournal(
      int page,
      int size,
      String sortBy,
      Sort.Direction order,
      boolean paginate,
      String sourceType,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
      UUID ownerId) {

    PaginationRequest pagination = new PaginationRequest(page, size, sortBy, order, paginate);
    Page<JournalEntryDto> result =
        listJournalEntriesUseCase.execute(pagination, sourceType, startDate, endDate, ownerId);

    return ApiResult.ok(result);
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_VIEWER')")
  public ResponseEntity<ApiResult<BalanceDto>> getBalance(String code, String currency) {
    List<LedgerAccount> accounts = accountRepository.findByCodeAndCurrency(code, currency);

    if (accounts.isEmpty()) {
      return ApiResult.error(
          HttpStatus.NOT_FOUND,
          "No accounts found for code: " + code + " with currency: " + currency,
          ErrorCode.RESOURCE_NOT_FOUND);
    }

    // Aggregate balance across all shards for system accounts
    var totalBalance =
        accounts.stream()
            .map(LedgerAccount::getBalance)
            .reduce(
                com.shadhinpay.common.money.Money.zero(currency),
                com.shadhinpay.common.money.Money::add);

    LedgerAccount first = accounts.get(0);
    BalanceDto dto =
        new BalanceDto(
            null, // accountId not meaningful for aggregated view
            code,
            first.getType().name(),
            currency,
            totalBalance.amount().toPlainString());

    return ApiResult.ok(dto);
  }

  @Override
  @PreAuthorize("hasAuthority('ADMIN_MANAGER')")
  public ResponseEntity<ApiResult<TrialBalanceReportDto>> getTrialBalance() {
    TrialBalanceReportDto report = verifyTrialBalanceUseCase.execute();
    return ApiResult.ok(report);
  }
}
