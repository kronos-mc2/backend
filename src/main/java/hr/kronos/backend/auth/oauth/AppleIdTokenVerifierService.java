package hr.kronos.backend.auth.oauth;

import hr.kronos.backend.auth.SocialIdentity;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AppleIdTokenVerifierService {
  private static final Logger logger = LoggerFactory.getLogger(AppleIdTokenVerifierService.class);
  private static final String APPLE_JWKS_URL = "https://appleid.apple.com/auth/keys";
  private static final String APPLE_ISSUER = "https://appleid.apple.com";

  private final JwtDecoder jwtDecoder;
  private final boolean enabled;

  public AppleIdTokenVerifierService(@Value("${auth.apple.client-id:}") String appleClientId) {
    String audience = appleClientId == null ? "" : appleClientId.trim();

    this.enabled = !audience.isBlank();
    if (!enabled) {
      this.jwtDecoder = null;
      return;
    }

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(APPLE_JWKS_URL).build();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(APPLE_ISSUER),
            new AudienceValidator(Set.of(audience)));
    decoder.setJwtValidator(validator);
    this.jwtDecoder = decoder;
  }

  public SocialIdentity verify(String idToken) {
    if (!enabled || jwtDecoder == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Apple login is not configured on backend.");
    }

    if (idToken == null || idToken.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Apple idToken is required.");
    }

    try {
      Jwt jwt = jwtDecoder.decode(idToken);
      String email = jwt.getClaimAsString("email");

      Object emailVerifiedClaim = jwt.getClaims().get("email_verified");
      boolean emailVerified =
          Boolean.TRUE.equals(emailVerifiedClaim)
              || "true".equalsIgnoreCase(String.valueOf(emailVerifiedClaim));

      if (email == null || email.isBlank() || !emailVerified) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Apple account email is not verified.");
      }

      String providerSubject = jwt.getSubject();
      if (providerSubject == null || providerSubject.isBlank()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Apple account subject is missing.");
      }

      return new SocialIdentity(providerSubject, email, jwt.getClaimAsString("name"));
    } catch (JwtException exception) {
      logger.warn("Apple id token verification failed: {}", exception.getMessage());
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Apple token.");
    }
  }
}
