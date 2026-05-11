package pay.conflux.backend.ledger.controller;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.ledger.constant.LedgerRoutes;
import pay.conflux.backend.ledger.dto.BalanceDto;
import pay.conflux.backend.ledger.dto.JournalEntryDto;
import pay.conflux.backend.ledger.dto.TrialBalanceReportDto;

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
