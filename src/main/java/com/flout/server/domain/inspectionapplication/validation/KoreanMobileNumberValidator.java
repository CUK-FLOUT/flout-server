package com.flout.server.domain.inspectionapplication.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class KoreanMobileNumberValidator
    implements ConstraintValidator<KoreanMobileNumber, String> {
  private static final Pattern MOBILE_NUMBER =
      Pattern.compile("^(?:01[016789]-\\d{3,4}-\\d{4}|01[016789]\\d{7,8})$");

  @Override
  public boolean isValid(String value, ConstraintValidatorContext context) {
    return value == null || MOBILE_NUMBER.matcher(value).matches();
  }
}
