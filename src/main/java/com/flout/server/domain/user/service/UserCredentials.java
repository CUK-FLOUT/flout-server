package com.flout.server.domain.user.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class UserCredentials {
  private static final int BCRYPT_PASSWORD_MAX_BYTES = 72;

  private UserCredentials() {}

  static String normalizeEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }

  static boolean isPasswordWithinBcryptLimit(String password) {
    return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_PASSWORD_MAX_BYTES;
  }
}
