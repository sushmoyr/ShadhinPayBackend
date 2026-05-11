package com.shadhinpay.ledger.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.common.dto.PaginationRequest;
import com.shadhinpay.common.error.ResourceNotFoundException;
import com.shadhinpay.common.money.Money;
import com.shadhinpay.common.security.SecurityUtils;
import com.shadhinpay.ledger.dto.BalanceDto;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.usecase.GetAccountBalanceUseCase;
import com.shadhinpay.ledger.usecase.internal.ListJournalEntriesUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MerchantLedgerControllerImpl implements MerchantLedgerController {

  private static final String DEFAULT_ACCOUNT_CODE = "MERCHANT_PAYABLE";

  private final GetAccountBalanceUseCase getAccountBalanceUseCase;
  private final ListJournalEntriesUseCase listJournalEntriesUseCase;

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<BalanceDto>> getBalance(
      @RequestParam(defaultValue = DEFAULT_ACCOUNT_CODE) String code,
      @RequestParam(defaultValue = "BDT") String currency) {
    UUID merchantId =
        SecurityUtils.currentMerchantId()
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated merchant", "current"));

    Money balance = getAccountBalanceUseCase.execute(merchantId, code);
    return ApiResult.ok(
        new BalanceDto(null, code, "LIABILITY", currency, balance.amount().toPlainString()));
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<List<JournalEntryDto>>> listJournal(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "occurredAt") String sortBy,
      @RequestParam(defaultValue = "DESC") Sort.Direction order,
      @RequestParam(defaultValue = "true") boolean paginate) {

    UUID merchantId =
        SecurityUtils.currentMerchantId()
            .orElseThrow(() -> new ResourceNotFoundException("Authenticated merchant", "current"));

    PaginationRequest pagination = new PaginationRequest(page, size, sortBy, order, paginate);
    Page<JournalEntryDto> result =
        listJournalEntriesUseCase.execute(pagination, null, null, null, merchantId);

    return ApiResult.ok(result);
  }
}
