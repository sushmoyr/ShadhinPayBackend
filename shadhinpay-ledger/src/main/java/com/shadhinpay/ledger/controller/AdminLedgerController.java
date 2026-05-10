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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping(LedgerRoutes.ADMIN_LEDGER_BASE)
public interface AdminLedgerController {

  @GetMapping("/journal")
  ResponseEntity<ApiResult<List<JournalEntryDto>>> listJournal(
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
      @RequestParam(required = false) UUID ownerId);

  @GetMapping("/balance/{code}")
  ResponseEntity<ApiResult<BalanceDto>> getBalance(
      @PathVariable String code, @RequestParam(defaultValue = "BDT") String currency);

  @GetMapping("/trial-balance")
  ResponseEntity<ApiResult<TrialBalanceReportDto>> getTrialBalance();
}
