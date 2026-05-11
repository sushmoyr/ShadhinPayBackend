package pay.conflux.backend.ledger.usecase.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pay.conflux.backend.common.money.Money;
import pay.conflux.backend.ledger.entity.LedgerAccount;
import pay.conflux.backend.ledger.entity.LedgerAccountType;
import pay.conflux.backend.ledger.repository.LedgerAccountRepository;

@ExtendWith(MockitoExtension.class)
class GetAccountBalanceUseCaseImplTest {

  @Mock private LedgerAccountRepository accountRepository;
  @InjectMocks private GetAccountBalanceUseCaseImpl useCase;

  @Test
  void shouldReturnZeroIfNoAccountsFound() {
    when(accountRepository.findByCodeAndCurrency("ESCROW", "BDT")).thenReturn(List.of());

    Money balance = useCase.execute(null, "ESCROW");

    assertThat(balance.amount()).isEqualByComparingTo("0");
    assertThat(balance.currency()).isEqualTo("BDT");
  }

  @Test
  void shouldSumBalancesForSystemAccounts() {
    LedgerAccount shard0 = new LedgerAccount(null, LedgerAccountType.ASSET, "ESCROW", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(
        shard0, "balance", Money.of(100, "BDT"));

    LedgerAccount shard1 = new LedgerAccount(null, LedgerAccountType.ASSET, "ESCROW", 1, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(
        shard1, "balance", Money.of(250, "BDT"));

    when(accountRepository.findByCodeAndCurrency("ESCROW", "BDT"))
        .thenReturn(List.of(shard0, shard1));

    Money balance = useCase.execute(null, "ESCROW");

    assertThat(balance.amount()).isEqualByComparingTo("350");
  }

  @Test
  void shouldReturnMerchantBalance() {
    UUID merchantId = UUID.randomUUID();
    LedgerAccount merchantAcc =
        new LedgerAccount(merchantId, LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(
        merchantAcc, "balance", Money.of(500, "BDT"));

    when(accountRepository.findByOwnerIdAndCodeAndShardIdAndCurrency(
            merchantId, "MERCHANT_PAYABLE", 0, "BDT"))
        .thenReturn(java.util.Optional.of(merchantAcc));

    Money balance = useCase.execute(merchantId, "MERCHANT_PAYABLE");

    assertThat(balance.amount()).isEqualByComparingTo("500");
  }
}
