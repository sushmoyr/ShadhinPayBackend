package pay.conflux.backend.common.validator;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmailValidatorTest {

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

  record Holder(@Email String email) {}

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"a@b.co", "first.last@sub.example.com", "x+tag@host.io"})
  void valid(String email) {
    assertThat(validator.validate(new Holder(email))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-an-email", "user@", "@host.com", "user@@host.com", "user @host.com"})
  void invalid(String email) {
    assertThat(validator.validate(new Holder(email))).isNotEmpty();
  }
}
