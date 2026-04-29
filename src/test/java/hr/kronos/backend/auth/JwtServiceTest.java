package hr.kronos.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import hr.kronos.backend.auth.persistence.UserRow;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
  private static final String VALID_SECRET = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";

  @Test
  void createTokenIncludesExpectedClaims() {
    JwtService jwtService = new JwtService(VALID_SECRET, 3600);
    UserRow user = buildUser();

    var claims = jwtService.parseClaims(jwtService.createToken(user));

    assertEquals("usr_123", claims.getSubject());
    assertEquals("user@example.com", claims.get("email", String.class));
    assertEquals("Test User", claims.get("name", String.class));
  }

  @Test
  void fallsBackToDefaultExpirationWhenConfiguredValueIsNonPositive() {
    JwtService jwtService = new JwtService(VALID_SECRET, 0);
    UserRow user = buildUser();

    var claims = jwtService.parseClaims(jwtService.createToken(user));
    long durationSeconds =
        claims.getExpiration().toInstant().getEpochSecond()
            - claims.getIssuedAt().toInstant().getEpochSecond();

    assertEquals(604800L, durationSeconds);
  }

  @Test
  void rejectsSecretsShorterThanThirtyTwoBytes() {
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> new JwtService("c2hvcnQtc2VjcmV0", 3600));

    assertEquals("auth.jwt.secret must be at least 32 bytes long.", exception.getMessage());
  }

  @Test
  void acceptsPlainTextSecretsWhenTheyAreNotBase64() {
    JwtService jwtService = new JwtService("local_dev_jwt_secret_change_me_32_bytes_minimum", 3600);
    UserRow user = buildUser();

    var claims = jwtService.parseClaims(jwtService.createToken(user));

    assertEquals("usr_123", claims.getSubject());
  }

  @Test
  void rejectsExpirationsLongerThanThirtyDays() {
    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> new JwtService(VALID_SECRET, 2592001));

    assertEquals("auth.jwt.expiration-seconds must be 30 days or less.", exception.getMessage());
  }

  private UserRow buildUser() {
    UserRow user = new UserRow();
    user.setId("usr_123");
    user.setEmail("user@example.com");
    user.setFullName("Test User");
    return user;
  }
}
