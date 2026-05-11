package pay.conflux.backend.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SafeStringValidator implements ConstraintValidator<SafeString, String> {

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '<' || c == '>') {
        return false;
      }
      if (Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r') {
        return false;
      }
    }
    return true;
  }
}
