package hr.kronos.backend.auth;

import java.util.regex.Pattern;

public final class PasswordPolicy {
  private PasswordPolicy() {}

  private static final Pattern PATTERN =
      Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,}$");

  public static boolean isValid(String password) {
    return password != null && PATTERN.matcher(password).matches();
  }
}
