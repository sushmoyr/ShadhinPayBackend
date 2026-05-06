package com.shadhinpay.common.util;

import java.util.regex.Pattern;

public final class IdentifierDetector {

  private static final Pattern PHONE_PATTERN = Pattern.compile("^(?:\\+?88)?01[3-9]\\d{8}$");
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
  private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{2,31}$");

  private IdentifierDetector() {}

  public static IdentifierType detect(String identifier) {
    if (identifier == null) {
      throw new IllegalArgumentException("identifier must not be null");
    }
    String trimmed = identifier.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("identifier must not be blank");
    }
    if (PHONE_PATTERN.matcher(trimmed).matches()) {
      return IdentifierType.PHONE;
    }
    if (EMAIL_PATTERN.matcher(trimmed).matches()) {
      return IdentifierType.EMAIL;
    }
    if (USERNAME_PATTERN.matcher(trimmed).matches()) {
      return IdentifierType.USERNAME;
    }
    throw new IllegalArgumentException("Unrecognized identifier format: " + identifier);
  }
}
