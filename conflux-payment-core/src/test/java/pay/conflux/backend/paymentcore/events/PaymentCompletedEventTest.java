package pay.conflux.backend.paymentcore.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.money.Money;

class PaymentCompletedEventTest {

  private static final UUID TXN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID MERCHANT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final UUID BUSINESS = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
  private static final Money AMOUNT = Money.of(new BigDecimal("250.0000"), "BDT");
  private static final Money FEE = Money.of(new BigDecimal("2.5000"), "BDT");
  private static final String VENDOR = "BKASH";
  private static final String MODE = "PARTNER";
  private static final String ORDER_REF = "MO-7";
  private static final Map<String, String> META = Map.of("invoice_id", "inv-1");
  private static final Instant AT = Instant.parse("2026-05-05T12:34:56Z");
  private static final String TRACE = "trace-c";
  private static final String VENDOR_TXN = "BKS-9001";

  @Test
  void rejects_null_vendorTransactionId() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentCompletedEvent(
                    TXN, MERCHANT, BUSINESS, AMOUNT, VENDOR, MODE, ORDER_REF, META, AT, TRACE, null,
                    FEE))
        .withMessageContaining("vendorTransactionId");
  }

  @Test
  void rejects_null_platformFee() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentCompletedEvent(
                    TXN,
                    MERCHANT,
                    BUSINESS,
                    AMOUNT,
                    VENDOR,
                    MODE,
                    ORDER_REF,
                    META,
                    AT,
                    TRACE,
                    VENDOR_TXN,
                    null))
        .withMessageContaining("platformFee");
  }

  @Test
  void rejects_null_amount() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentCompletedEvent(
                    TXN,
                    MERCHANT,
                    BUSINESS,
                    null,
                    VENDOR,
                    MODE,
                    ORDER_REF,
                    META,
                    AT,
                    TRACE,
                    VENDOR_TXN,
                    FEE))
        .withMessageContaining("amount");
  }

  @Test
  void rejects_null_traceId() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new PaymentCompletedEvent(
                    TXN,
                    MERCHANT,
                    BUSINESS,
                    AMOUNT,
                    VENDOR,
                    MODE,
                    ORDER_REF,
                    META,
                    AT,
                    null,
                    VENDOR_TXN,
                    FEE))
        .withMessageContaining("traceId");
  }

  @Test
  void null_metadata_is_replaced_with_empty_map() {
    PaymentCompletedEvent e =
        new PaymentCompletedEvent(
            TXN,
            MERCHANT,
            BUSINESS,
            AMOUNT,
            VENDOR,
            MODE,
            ORDER_REF,
            null,
            AT,
            TRACE,
            VENDOR_TXN,
            FEE);
    assertThat(e.metadata()).isEmpty();
  }

  @Test
  void equality_is_value_based() {
    PaymentCompletedEvent a =
        new PaymentCompletedEvent(
            TXN,
            MERCHANT,
            BUSINESS,
            AMOUNT,
            VENDOR,
            MODE,
            ORDER_REF,
            META,
            AT,
            TRACE,
            VENDOR_TXN,
            FEE);
    PaymentCompletedEvent b =
        new PaymentCompletedEvent(
            TXN,
            MERCHANT,
            BUSINESS,
            AMOUNT,
            VENDOR,
            MODE,
            ORDER_REF,
            META,
            AT,
            TRACE,
            VENDOR_TXN,
            FEE);
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
  }
}
