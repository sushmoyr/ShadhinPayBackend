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

class PhoneNumberValidatorTest {

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

  record Holder(@PhoneNumber String phone) {}

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"01712345678", "+8801912345678", "8801512345678"})
  void valid(String phone) {
    assertThat(validator.validate(new Holder(phone))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "123", "01212345678", "0171234567a", "++8801712345678"})
  void invalid(String phone) {
    assertThat(validator.validate(new Holder(phone))).isNotEmpty();
  }

  @Test
  void messageIsCustomisable() {
    PhoneNumberValidator v = new PhoneNumberValidator();
    assertThat(v.isValid(null, null)).isTrue();
    assertThat(v.isValid("01712345678", null)).isTrue();
    assertThat(v.isValid("nope", null)).isFalse();
  }
}
