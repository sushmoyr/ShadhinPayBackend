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
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping(LedgerRoutes.MERCHANT_BASE)
public interface MerchantLedgerController {

  @GetMapping("/balance")
  ResponseEntity<ApiResult<BalanceDto>> getBalance(
      @RequestParam(defaultValue = "MERCHANT_PAYABLE") String code,
      @RequestParam(defaultValue = "BDT") String currency);

  @GetMapping("/journal")
  ResponseEntity<ApiResult<List<JournalEntryDto>>> listJournal(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "occurredAt") String sortBy,
      @RequestParam(defaultValue = "DESC") Sort.Direction order,
      @RequestParam(defaultValue = "true") boolean paginate);
}
