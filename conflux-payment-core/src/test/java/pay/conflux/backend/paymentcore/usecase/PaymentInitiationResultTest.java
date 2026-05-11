package pay.conflux.backend.paymentcore.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentInitiationResultTest {

  private static final UUID TXN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final String REDIRECT = "https://pay.bkash.com/execute?ref=BKS-001";
  private static final String STATUS = "INITIATED";

  @Test
  void rejects_null_transactionId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PaymentInitiationResult(null, REDIRECT, STATUS))
        .withMessageContaining("transactionId");
  }

  @Test
  void rejects_null_redirectUrl() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PaymentInitiationResult(TXN, null, STATUS))
        .withMessageContaining("redirectUrl");
  }

  @Test
  void rejects_null_status() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PaymentInitiationResult(TXN, REDIRECT, null))
        .withMessageContaining("status");
  }

  @Test
  void accessors_return_constructed_values() {
    PaymentInitiationResult result = new PaymentInitiationResult(TXN, REDIRECT, STATUS);
    assertThat(result.transactionId()).isEqualTo(TXN);
    assertThat(result.redirectUrl()).isEqualTo(REDIRECT);
    assertThat(result.status()).isEqualTo(STATUS);
  }

  @Test
  void equality_is_value_based() {
    PaymentInitiationResult a = new PaymentInitiationResult(TXN, REDIRECT, STATUS);
    PaymentInitiationResult b = new PaymentInitiationResult(TXN, REDIRECT, STATUS);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}
