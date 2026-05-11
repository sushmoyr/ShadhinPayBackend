package pay.conflux.backend.ledger.usecase.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import pay.conflux.backend.ledger.repository.PostingRepository;

@ExtendWith(MockitoExtension.class)
class VerifyTrialBalanceUseCaseImplTest {

  @Mock private LedgerAccountRepository accountRepository;
  @Mock private PostingRepository postingRepository;

  @InjectMocks private VerifyTrialBalanceUseCaseImpl useCase;

  @Test
  void shouldReturnCleanReportWhenLedgerIsBalanced() {
    UUID assetId = UUID.randomUUID();
    UUID liabilityId = UUID.randomUUID();

    LedgerAccount asset =
        new LedgerAccount(UUID.randomUUID(), LedgerAccountType.ASSET, "ESCROW", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(asset, "id", assetId);
    org.springframework.test.util.ReflectionTestUtils.setField(
        asset, "balance", Money.of(100, "BDT"));

    LedgerAccount liability =
        new LedgerAccount(
            UUID.randomUUID(), LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(liability, "id", liabilityId);
    org.springframework.test.util.ReflectionTestUtils.setField(
        liability, "balance", Money.of(100, "BDT"));

    when(accountRepository.findAll()).thenReturn(List.of(asset, liability));
    // For ASSET: signed sum should equal balance → signed sum = 100
    when(postingRepository.sumAmountByAccountId(assetId)).thenReturn(new BigDecimal("100.0000"));
    // For LIABILITY: signed sum should equal -balance → signed sum = -100
    when(postingRepository.sumAmountByAccountId(liabilityId))
        .thenReturn(new BigDecimal("-100.0000"));

    var report = useCase.execute();

    assertThat(report.globalSumZero()).isTrue();
    assertThat(report.balanceMismatches()).isEmpty();
    assertThat(report.generatedAt()).isNotNull();
  }

  @Test
  void shouldReportMismatchWhenBalancesDontMatchSumOfPostings() {
    UUID accountId = UUID.randomUUID();
    LedgerAccount asset = new LedgerAccount(accountId, LedgerAccountType.ASSET, "ESCROW", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(asset, "id", accountId);
    org.springframework.test.util.ReflectionTestUtils.setField(
        asset, "balance", Money.of(95, "BDT"));

    when(accountRepository.findAll()).thenReturn(List.of(asset));
    // Postings sum to 100, but cached balance is 95 → mismatch
    when(postingRepository.sumAmountByAccountId(accountId)).thenReturn(new BigDecimal("100.0000"));

    var report = useCase.execute();

    assertThat(report.globalSumZero()).isFalse();
    assertThat(report.balanceMismatches()).hasSize(1);
    assertThat(report.balanceMismatches().get(0).accountId()).isEqualTo(accountId);
    assertThat(report.balanceMismatches().get(0).accountCode()).isEqualTo("ESCROW");
    assertThat(report.balanceMismatches().get(0).expectedBalance()).isEqualTo("100.0000");
    assertThat(report.balanceMismatches().get(0).actualBalance()).isEqualTo("95.0000");
    assertThat(report.balanceMismatches().get(0).delta()).isEqualTo("-5.0000");
  }

  @Test
  void shouldHandleNullSumFromRepository() {
    UUID accountId = UUID.randomUUID();
    LedgerAccount asset =
        new LedgerAccount(UUID.randomUUID(), LedgerAccountType.ASSET, "ESCROW", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(asset, "id", accountId);
    org.springframework.test.util.ReflectionTestUtils.setField(asset, "balance", Money.zero("BDT"));

    when(accountRepository.findAll()).thenReturn(List.of(asset));
    when(postingRepository.sumAmountByAccountId(accountId)).thenReturn(null);

    var report = useCase.execute();

    assertThat(report.globalSumZero()).isTrue();
    assertThat(report.balanceMismatches()).isEmpty();
  }

  @Test
  void shouldHandleLiabilityAccountCorrectly() {
    UUID liabilityId = UUID.randomUUID();
    LedgerAccount liability =
        new LedgerAccount(
            UUID.randomUUID(), LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(liability, "id", liabilityId);
    // For LIABILITY (debitSign = -1): balance should equal -signedSum
    // balance = 200, so signed sum should = -200
    org.springframework.test.util.ReflectionTestUtils.setField(
        liability, "balance", Money.of(200, "BDT"));

    when(accountRepository.findAll()).thenReturn(List.of(liability));
    when(postingRepository.sumAmountByAccountId(liabilityId))
        .thenReturn(new BigDecimal("-200.0000"));

    var report = useCase.execute();

    assertThat(report.globalSumZero()).isFalse(); // global sum = -200, not zero
    assertThat(report.balanceMismatches()).isEmpty(); // but per-account it matches
  }

  @Test
  void shouldDetectMismatchForLiabilityAccount() {
    UUID liabilityId = UUID.randomUUID();
    LedgerAccount liability =
        new LedgerAccount(
            UUID.randomUUID(), LedgerAccountType.LIABILITY, "MERCHANT_PAYABLE", 0, "BDT");
    org.springframework.test.util.ReflectionTestUtils.setField(liability, "id", liabilityId);
    org.springframework.test.util.ReflectionTestUtils.setField(
        liability, "balance", Money.of(200, "BDT"));

    when(accountRepository.findAll()).thenReturn(List.of(liability));
    // Signed sum = -150 → expected balance = 150, actual = 200 → mismatch of +50
    when(postingRepository.sumAmountByAccountId(liabilityId))
        .thenReturn(new BigDecimal("-150.0000"));

    var report = useCase.execute();

    assertThat(report.balanceMismatches()).hasSize(1);
    assertThat(report.balanceMismatches().get(0).expectedBalance()).isEqualTo("150.0000");
    assertThat(report.balanceMismatches().get(0).actualBalance()).isEqualTo("200.0000");
    assertThat(report.balanceMismatches().get(0).delta()).isEqualTo("50.0000");
  }
}
