package pay.conflux.backend.adapters.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.money.Money;

class VendorPaymentRequestTest {

  private static final UUID TXN_ID = UUID.randomUUID();
  private static final Money AMOUNT = Money.of(100, "BDT");
  private static final String ORDER_REF = "order-001";
  private static final String CALLBACK = "https://example.com/callback";

  @Test
  void rejects_null_transactionId() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VendorPaymentRequest(null, AMOUNT, ORDER_REF, CALLBACK, Map.of()))
        .withMessageContaining("transactionId");
  }

  @Test
  void rejects_null_amount() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VendorPaymentRequest(TXN_ID, null, ORDER_REF, CALLBACK, Map.of()))
        .withMessageContaining("amount");
  }

  @Test
  void rejects_null_merchantOrderReference() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VendorPaymentRequest(TXN_ID, AMOUNT, null, CALLBACK, Map.of()))
        .withMessageContaining("merchantOrderReference");
  }

  @Test
  void rejects_null_callbackUrl() {
    assertThatNullPointerException()
        .isThrownBy(() -> new VendorPaymentRequest(TXN_ID, AMOUNT, ORDER_REF, null, Map.of()))
        .withMessageContaining("callbackUrl");
  }

  @Test
  void null_metadata_defaults_to_empty_map() {
    VendorPaymentRequest req = new VendorPaymentRequest(TXN_ID, AMOUNT, ORDER_REF, CALLBACK, null);
    assertThat(req.metadata()).isEmpty();
  }

  @Test
  void metadata_is_defensively_copied() {
    Map<String, String> mutable = new HashMap<>();
    mutable.put("channel", "web");
    VendorPaymentRequest req =
        new VendorPaymentRequest(TXN_ID, AMOUNT, ORDER_REF, CALLBACK, mutable);
    mutable.put("extra", "should-not-appear");
    assertThat(req.metadata()).hasSize(1).containsEntry("channel", "web");
  }

  @Test
  void accessors_return_constructed_values() {
    Map<String, String> meta = Map.of("k", "v");
    VendorPaymentRequest req = new VendorPaymentRequest(TXN_ID, AMOUNT, ORDER_REF, CALLBACK, meta);
    assertThat(req.transactionId()).isEqualTo(TXN_ID);
    assertThat(req.amount()).isEqualTo(AMOUNT);
    assertThat(req.merchantOrderReference()).isEqualTo(ORDER_REF);
    assertThat(req.callbackUrl()).isEqualTo(CALLBACK);
    assertThat(req.metadata()).containsEntry("k", "v");
  }
}
