package pay.conflux.backend.paymentcore.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.paymentcore.dto.PaymentResponseDto;
import pay.conflux.backend.paymentcore.dto.TransactionSummaryDto;
import pay.conflux.backend.paymentcore.entity.Transaction;
import pay.conflux.backend.paymentcore.entity.TransactionMode;
import pay.conflux.backend.paymentcore.entity.TransactionStatus;

class TransactionMapperTest {

  private final TransactionMapper mapper = new TransactionMapperImpl();

  private Transaction sample(LocalDateTime createdAt) {
    Transaction tx = new Transaction();
    tx.setId(UUID.randomUUID());
    tx.setBusinessId(UUID.randomUUID());
    tx.setMerchantId(UUID.randomUUID());
    tx.setStatus(TransactionStatus.PENDING);
    tx.setMode(TransactionMode.PARTNER);
    tx.setVendor("MOCK");
    tx.setAmountValue(new BigDecimal("12.34"));
    tx.setAmountCurrency("BDT");
    tx.setMerchantOrderReference("o-1");
    tx.setCreatedAt(createdAt);
    return tx;
  }

  @Test
  void toResponseDto_withTimestamp_copiesAndConvertsCreatedAt() {
    Transaction tx = sample(LocalDateTime.of(2026, 1, 1, 12, 0, 0));
    PaymentResponseDto dto = mapper.toResponseDto(tx);
    assertThat(dto.getTransactionId()).isEqualTo(tx.getId());
    assertThat(dto.getAmount()).isEqualByComparingTo("12.34");
    assertThat(dto.getCurrency()).isEqualTo("BDT");
    assertThat(dto.getStatus()).isEqualTo(TransactionStatus.PENDING.name());
    assertThat(dto.getCreatedAt()).isNotNull();
  }

  @Test
  void toResponseDto_withNullCreatedAt_doesNotThrow() {
    Transaction tx = sample(null);
    PaymentResponseDto dto = mapper.toResponseDto(tx);
    assertThat(dto.getCreatedAt()).isNull();
  }

  @Test
  void toResponseDto_withNullEntity_returnsNull() {
    assertThat(mapper.toResponseDto(null)).isNull();
  }

  @Test
  void toSummaryDto_copiesIdentityFields() {
    Transaction tx = sample(LocalDateTime.of(2026, 2, 2, 8, 0));
    TransactionSummaryDto dto = mapper.toSummaryDto(tx);
    assertThat(dto.getId()).isEqualTo(tx.getId());
    assertThat(dto.getAmount()).isEqualByComparingTo("12.34");
    assertThat(dto.getCurrency()).isEqualTo("BDT");
  }

  @Test
  void toSummaryDto_withNullEntity_returnsNull() {
    assertThat(mapper.toSummaryDto(null)).isNull();
  }
}
