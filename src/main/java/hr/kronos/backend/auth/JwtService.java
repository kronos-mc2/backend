package hr.kronos.backend.auth;

import hr.kronos.backend.auth.persistence.UserRow;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private static final long DEFAULT_EXPIRATION_SECONDS = 604800;
  private static final long MAX_EXPIRATION_SECONDS = 2592000;

  private final SecretKey secretKey;
  private final long expirationSeconds;

  public JwtService(
      @Value("${auth.jwt.secret}") String jwtSecret,
      @Value("${auth.jwt.expiration-seconds:604800}") long expirationSeconds) {
    this.secretKey = buildSecretKey(jwtSecret);
    this.expirationSeconds = normalizeExpirationSeconds(expirationSeconds);
  }

  public String createToken(UserRow user) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(expirationSeconds);

    return Jwts.builder()
        .subject(user.getId())
        .claim("email", user.getEmail())
        .claim("name", user.getFullName())
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(secretKey)
        .compact();
  }

  public Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }

  private SecretKey buildSecretKey(String jwtSecret) {
    if (jwtSecret == null || jwtSecret.isBlank()) {
      throw new IllegalStateException("auth.jwt.secret must be configured.");
    }

    byte[] bytes;
    try {
      bytes = Decoders.BASE64.decode(jwtSecret);
    } catch (IllegalArgumentException ignored) {
      bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
    }

    if (bytes.length < 32) {
      throw new IllegalStateException("auth.jwt.secret must be at least 32 bytes long.");
    }

    return Keys.hmacShaKeyFor(bytes);
  }

  private long normalizeExpirationSeconds(long configuredExpirationSeconds) {
    long normalized = configuredExpirationSeconds <= 0 ? DEFAULT_EXPIRATION_SECONDS : configuredExpirationSeconds;
    if (normalized > MAX_EXPIRATION_SECONDS) {
      throw new IllegalStateException("auth.jwt.expiration-seconds must be 30 days or less.");
    }
    return normalized;
  }
}
