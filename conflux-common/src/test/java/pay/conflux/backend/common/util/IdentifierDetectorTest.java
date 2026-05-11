package pay.conflux.backend.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IdentifierDetectorTest {

  @ParameterizedTest
  @ValueSource(strings = {"01712345678", "+8801712345678", "8801912345678", "01612345678"})
  void detectsBdPhoneNumbers(String value) {
    assertThat(IdentifierDetector.detect(value)).isEqualTo(IdentifierType.PHONE);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "user@example.com",
        "first.last@sub.example.co",
        "admin+tag@conflux.com",
        "x@y.io"
      })
  void detectsEmails(String value) {
    assertThat(IdentifierDetector.detect(value)).isEqualTo(IdentifierType.EMAIL);
  }

  @ParameterizedTest
  @ValueSource(strings = {"alice", "bob_42", "merchant.one", "AdminUser"})
  void detectsUsernames(String value) {
    assertThat(IdentifierDetector.detect(value)).isEqualTo(IdentifierType.USERNAME);
  }

  @ParameterizedTest
  @ValueSource(strings = {"01212345678", "0171234567", "++8801712345678", "not an id!", "@@@"})
  void rejectsGarbage(String value) {
    assertThatThrownBy(() -> IdentifierDetector.detect(value))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullAndBlank() {
    assertThatThrownBy(() -> IdentifierDetector.detect(null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> IdentifierDetector.detect("   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void trimsWhitespaceBeforeMatching() {
    assertThat(IdentifierDetector.detect("  01712345678  ")).isEqualTo(IdentifierType.PHONE);
  }
}
