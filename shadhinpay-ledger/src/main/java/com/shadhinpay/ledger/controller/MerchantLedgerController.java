package com.shadhinpay.ledger.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.ledger.constant.LedgerRoutes;
import com.shadhinpay.ledger.dto.BalanceDto;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping(LedgerRoutes.MERCHANT_BASE)
public interface MerchantLedgerController {

  @GetMapping("/balance")
  ResponseEntity<ApiResult<BalanceDto>> getBalance(String code, String currency);

  @GetMapping("/journal")
  ResponseEntity<ApiResult<List<JournalEntryDto>>> listJournal(
      int page, int size, String sortBy, Sort.Direction order, boolean paginate);
}
