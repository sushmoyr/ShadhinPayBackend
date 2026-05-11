package pay.conflux.backend.common.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.internal.constraintvalidators.AbstractEmailValidator;

public class EmailValidator implements ConstraintValidator<Email, CharSequence> {

  private final AbstractEmailValidator<Email> delegate = new AbstractEmailValidator<>() {};

  @Override
  public void initialize(Email constraintAnnotation) {
    delegate.initialize(constraintAnnotation);
  }

  @Override
  public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    return delegate.isValid(value, context);
  }
}
