package pay.conflux.backend.common.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pay.conflux.backend.common.crypto.HmacSigner;

class WebhookSignerTest {

  private final WebhookSigner signer = new WebhookSigner(new HmacSigner());

  @Test
  void signature_isDeterministicForSamePayloadAndSecret() {
    String payload = "{\"event\":\"payment.success\",\"id\":\"tx_123\"}";
    String a = signer.signatureFor(payload, "whsec_test");
    String b = signer.signatureFor(payload, "whsec_test");
    assertThat(a).isEqualTo(b);
  }

  @Test
  void signature_isHexEncoded64Chars() {
    String sig = signer.signatureFor("{}", "whsec_test");
    assertThat(sig).hasSize(64).matches("[0-9a-f]+");
  }

  @Test
  void singleByteChange_producesDifferentSignature() {
    String a = signer.signatureFor("{\"id\":\"1\"}", "whsec_test");
    String b = signer.signatureFor("{\"id\":\"2\"}", "whsec_test");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void differentSecret_producesDifferentSignature() {
    String a = signer.signatureFor("{}", "whsec_one");
    String b = signer.signatureFor("{}", "whsec_two");
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void verify_acceptsRoundTrip() {
    String payload = "{\"event\":\"payment.failed\"}";
    String sig = signer.signatureFor(payload, "whsec_test");
    assertThat(signer.verify(payload, "whsec_test", sig)).isTrue();
  }

  @Test
  void verify_rejectsTamperedPayload() {
    String sig = signer.signatureFor("{\"id\":\"a\"}", "whsec_test");
    assertThat(signer.verify("{\"id\":\"b\"}", "whsec_test", sig)).isFalse();
  }

  @Test
  void signatureHeaderConstant_matchesSpec() {
    assertThat(WebhookSigner.SIGNATURE_HEADER).isEqualTo("X-PGW-Signature");
  }
}
