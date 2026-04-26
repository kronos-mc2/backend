package hr.kronos.backend.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

  @Test
  void acceptsPasswordThatMatchesAllRequirements() {
    assertTrue(PasswordPolicy.isValid("Valid123!"));
  }

  @Test
  void rejectsPasswordThatIsTooShort() {
    assertFalse(PasswordPolicy.isValid("Va1!d"));
  }

  @Test
  void rejectsPasswordWithoutSpecialCharacter() {
    assertFalse(PasswordPolicy.isValid("Valid1234"));
  }

  @Test
  void rejectsNullPassword() {
    assertFalse(PasswordPolicy.isValid(null));
  }
}
