package pay.conflux.backend.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {

  private static final Pattern PATTERN = Pattern.compile("^(?:\\+?88)?01[3-9]\\d{8}$");

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return PATTERN.matcher(value).matches();
  }
}
