package pay.conflux.backend.paymentcore.property;

import static org.assertj.core.api.Assertions.assertThat;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import pay.conflux.backend.common.crypto.HmacSigner;
import pay.conflux.backend.common.webhook.WebhookSigner;

/**
 * For any (payload, secret) pair: {@link WebhookSigner#verify} agrees with {@link
 * WebhookSigner#signatureFor}; any single-bit modification to the payload breaks verification.
 */
class WebhookSignaturePropertyTest {

  private static final WebhookSigner SIGNER = new WebhookSigner(new HmacSigner());

  @Property(tries = 500)
  void signedPayloadVerifies(
      @ForAll @NotBlank @StringLength(min = 1, max = 256) String payload,
      @ForAll("secrets") String secret) {
    String signature = SIGNER.signatureFor(payload, secret);
    assertThat(SIGNER.verify(payload, secret, signature))
        .as("freshly-signed payload must verify")
        .isTrue();
  }

  @Property(tries = 500)
  void tamperedPayloadFailsVerification(
      @ForAll @NotBlank @StringLength(min = 4, max = 256) String payload,
      @ForAll("secrets") String secret) {
    String signature = SIGNER.signatureFor(payload, secret);
    String tampered = tamperFirstChar(payload);
    if (tampered.equals(payload)) {
      return; // skip when tampering produced the same string
    }
    assertThat(SIGNER.verify(tampered, secret, signature))
        .as("any modification to the payload must invalidate the signature")
        .isFalse();
  }

  @Property(tries = 500)
  void wrongSecretFailsVerification(
      @ForAll @NotBlank @StringLength(min = 1, max = 256) String payload,
      @ForAll("secrets") String secret,
      @ForAll("secrets") String otherSecret) {
    if (secret.equals(otherSecret)) {
      return;
    }
    String signature = SIGNER.signatureFor(payload, secret);
    assertThat(SIGNER.verify(payload, otherSecret, signature))
        .as("signature must not verify under a different secret")
        .isFalse();
  }

  @Provide
  Arbitrary<String> secrets() {
    return Arbitraries.strings()
        .withCharRange('!', '~')
        .ofMinLength(8)
        .ofMaxLength(64)
        .filter(s -> !s.isBlank());
  }

  private static String tamperFirstChar(String s) {
    char[] chars = s.toCharArray();
    chars[0] = chars[0] == 'A' ? 'B' : 'A';
    return new String(chars);
  }
}
