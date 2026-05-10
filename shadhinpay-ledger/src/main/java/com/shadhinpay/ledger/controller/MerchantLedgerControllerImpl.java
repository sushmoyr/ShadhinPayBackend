package com.shadhinpay.ledger.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.common.dto.PaginationRequest;
import com.shadhinpay.common.security.SecurityUtils;
import com.shadhinpay.ledger.dto.BalanceDto;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.entity.LedgerAccount;
import com.shadhinpay.ledger.mapper.LedgerMapper;
import com.shadhinpay.ledger.repository.LedgerAccountRepository;
import com.shadhinpay.ledger.usecase.internal.ListJournalEntriesUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class MerchantLedgerControllerImpl implements MerchantLedgerController {

  private final LedgerAccountRepository accountRepository;
  private final ListJournalEntriesUseCase listJournalEntriesUseCase;
  private final LedgerMapper mapper;

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<BalanceDto>> getBalance(String code, String currency) {
    UUID merchantId =
        SecurityUtils.currentMerchantId()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

    LedgerAccount account =
        accountRepository
            .findByOwnerIdAndCodeAndShardIdAndCurrency(merchantId, code, 0, currency)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found: " + code + " for merchant " + merchantId));

    return ApiResult.ok(mapper.toBalanceDto(account));
  }

  @Override
  @PreAuthorize("hasAuthority('MERCHANT')")
  public ResponseEntity<ApiResult<List<JournalEntryDto>>> listJournal(
      int page, int size, String sortBy, Sort.Direction order, boolean paginate) {

    UUID merchantId =
        SecurityUtils.currentMerchantId()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

    PaginationRequest pagination = new PaginationRequest(page, size, sortBy, order, paginate);
    Page<JournalEntryDto> result =
        listJournalEntriesUseCase.execute(pagination, null, null, null, merchantId);

    return ApiResult.ok(result);
  }
}
