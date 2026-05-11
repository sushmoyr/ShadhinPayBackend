package pay.conflux.backend.ledger.controller;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import pay.conflux.backend.common.dto.ApiResult;
import pay.conflux.backend.ledger.constant.LedgerRoutes;
import pay.conflux.backend.ledger.dto.BalanceDto;
import pay.conflux.backend.ledger.dto.JournalEntryDto;

@RequestMapping(LedgerRoutes.MERCHANT_BASE)
public interface MerchantLedgerController {

  @GetMapping("/balance")
  ResponseEntity<ApiResult<BalanceDto>> getBalance(String code, String currency);

  @GetMapping("/journal")
  ResponseEntity<ApiResult<List<JournalEntryDto>>> listJournal(
      int page, int size, String sortBy, Sort.Direction order, boolean paginate);
}
