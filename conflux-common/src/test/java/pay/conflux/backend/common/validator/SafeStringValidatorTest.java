package pay.conflux.backend.common.validator;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SafeStringValidatorTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  record Holder(@SafeString String value) {}

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"hello world", "tabs\tand\nnewlines\rok", "1234.56", "merchant-name_42"})
  void valid(String value) {
    assertThat(validator.validate(new Holder(value))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"<script>", "ok>but-bad", "ok<also-bad"})
  void rejectsAngleBrackets(String value) {
    assertThat(validator.validate(new Holder(value))).isNotEmpty();
  }

  @Test
  void rejectsControlCharacters() {
    SafeStringValidator v = new SafeStringValidator();

    assertThat(v.isValid("bell\u0007here", null)).isFalse();
    assertThat(v.isValid("null\u0000byte", null)).isFalse();
    assertThat(v.isValid("escape\u001bsequence", null)).isFalse();
  }

  @Test
  void allowsTabNewlineCarriageReturn() {
    SafeStringValidator v = new SafeStringValidator();

    assertThat(v.isValid("a\tb", null)).isTrue();
    assertThat(v.isValid("a\nb", null)).isTrue();
    assertThat(v.isValid("a\rb", null)).isTrue();
  }
}
