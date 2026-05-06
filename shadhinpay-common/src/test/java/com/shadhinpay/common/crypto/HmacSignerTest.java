package com.shadhinpay.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class HmacSignerTest {

  private final HmacSigner signer = new HmacSigner();

  @Test
  void rfc4231_testCase1() {
    // RFC 4231 §4.2 — key=0x0b*20, data="Hi There"
    byte[] keyBytes = new byte[20];
    java.util.Arrays.fill(keyBytes, (byte) 0x0b);
    String key = new String(keyBytes, StandardCharsets.ISO_8859_1);
    String mac = signer.sign("Hi There".getBytes(StandardCharsets.UTF_8), key);
    assertThat(mac).isEqualTo("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7");
  }

  @Test
  void rfc4231_testCase2() {
    // RFC 4231 §4.3 — key="Jefe", data="what do ya want for nothing?"
    String mac =
        signer.sign("what do ya want for nothing?".getBytes(StandardCharsets.UTF_8), "Jefe");
    assertThat(mac).isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
  }

  @Test
  void verify_acceptsValidSignature() {
    byte[] payload = "msg".getBytes(StandardCharsets.UTF_8);
    String sig = signer.sign(payload, "secret");
    assertThat(signer.verify(payload, "secret", sig)).isTrue();
  }

  @Test
  void verify_rejectsAlteredSignature() {
    byte[] payload = "msg".getBytes(StandardCharsets.UTF_8);
    String sig = signer.sign(payload, "secret");
    String altered = sig.substring(0, sig.length() - 1) + (sig.endsWith("0") ? '1' : '0');
    assertThat(signer.verify(payload, "secret", altered)).isFalse();
  }

  @Test
  void verify_rejectsDifferentLengthSignature() {
    byte[] payload = "msg".getBytes(StandardCharsets.UTF_8);
    assertThat(signer.verify(payload, "secret", "deadbeef")).isFalse();
  }

  @Test
  void verify_rejectsAlteredPayload() {
    String sig = signer.sign("a".getBytes(StandardCharsets.UTF_8), "secret");
    assertThat(signer.verify("b".getBytes(StandardCharsets.UTF_8), "secret", sig)).isFalse();
  }

  @Test
  void rejectsEmptySecret() {
    assertThatThrownBy(() -> signer.sign(new byte[1], ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
