package pay.conflux.backend.paymentcore.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.money.Money;

class PaymentRefundedEventTest {

  private static final UUID REFUND_TXN = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
  private static final UUID ORIGINAL = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final Money AMOUNT = Money.of(new BigDecimal("50.0000"), "BDT");
  private static final Map<String, String> META = Map.of("reason", "customer-request");
  private static final Instant AT = Instant.parse("2026-05-05T13:00:00Z");
  private static final String TRACE = "trace-r";

  @Test
  void rejects_null_originalTransactionId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PaymentRefundedEvent(REFUND_TXN, null, AMOUNT, META, AT, TRACE))
        .withMessageContaining("originalTransactionId");
  }

  @Test
  void rejects_null_amount() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PaymentRefundedEvent(REFUND_TXN, ORIGINAL, null, META, AT, TRACE))
        .withMessageContaining("amount");
  }

  @Test
  void rejects_null_traceId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PaymentRefundedEvent(REFUND_TXN, ORIGINAL, AMOUNT, META, AT, null))
        .withMessageContaining("traceId");
  }

  @Test
  void null_metadata_is_replaced_with_empty_map() {
    PaymentRefundedEvent e =
        new PaymentRefundedEvent(REFUND_TXN, ORIGINAL, AMOUNT, null, AT, TRACE);
    assertThat(e.metadata()).isEmpty();
  }

  @Test
  void equality_is_value_based() {
    PaymentRefundedEvent a =
        new PaymentRefundedEvent(REFUND_TXN, ORIGINAL, AMOUNT, META, AT, TRACE);
    PaymentRefundedEvent b =
        new PaymentRefundedEvent(REFUND_TXN, ORIGINAL, AMOUNT, META, AT, TRACE);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}
