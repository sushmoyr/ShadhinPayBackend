package com.shadhinpay.ledger.controller;

import com.shadhinpay.common.dto.ApiResult;
import com.shadhinpay.ledger.constant.LedgerRoutes;
import com.shadhinpay.ledger.dto.BalanceDto;
import com.shadhinpay.ledger.dto.JournalEntryDto;
import com.shadhinpay.ledger.dto.TrialBalanceReportDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping(LedgerRoutes.ADMIN_LEDGER_BASE)
public interface AdminLedgerController {

  @GetMapping("/journal")
  ResponseEntity<ApiResult<List<JournalEntryDto>>> listJournal(
      int page,
      int size,
      String sortBy,
      Sort.Direction order,
      boolean paginate,
      String sourceType,
      Instant startDate,
      Instant endDate,
      UUID ownerId);

  @GetMapping("/balance/{code}")
  ResponseEntity<ApiResult<BalanceDto>> getBalance(String code, String currency);

  @GetMapping("/trial-balance")
  ResponseEntity<ApiResult<TrialBalanceReportDto>> getTrialBalance();
}
