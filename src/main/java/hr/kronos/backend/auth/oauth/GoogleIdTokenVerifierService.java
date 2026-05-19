package hr.kronos.backend.auth.oauth;

import hr.kronos.backend.auth.SocialIdentity;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
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
public class GoogleIdTokenVerifierService {
  private static final Logger logger = LoggerFactory.getLogger(GoogleIdTokenVerifierService.class);
  private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
  private static final String GOOGLE_ISSUER = "https://accounts.google.com";

  private final JwtDecoder jwtDecoder;
  private final boolean enabled;

  public GoogleIdTokenVerifierService(@Value("${auth.google.client-ids:}") String googleClientIds) {
    Set<String> audiences =
        Arrays.stream(googleClientIds.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toSet());

    this.enabled = !audiences.isEmpty();
    if (!enabled) {
      this.jwtDecoder = null;
      return;
    }

    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URL).build();
    OAuth2TokenValidator<Jwt> validator =
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER), new AudienceValidator(audiences));
    decoder.setJwtValidator(validator);
    this.jwtDecoder = decoder;
  }

  public SocialIdentity verify(String idToken) {
    if (!enabled || jwtDecoder == null) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Google login is not configured on backend.");
    }

    if (idToken == null || idToken.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google idToken is required.");
    }

    try {
      Jwt jwt = jwtDecoder.decode(idToken);

      String email = jwt.getClaimAsString("email");
      Boolean emailVerified = jwt.getClaim("email_verified");

      if (email == null || email.isBlank() || !Boolean.TRUE.equals(emailVerified)) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account email is not verified.");
      }

      String providerSubject = jwt.getSubject();
      if (providerSubject == null || providerSubject.isBlank()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google account subject is missing.");
      }

      String name = jwt.getClaimAsString("name");
      return new SocialIdentity(providerSubject, email, name);
    } catch (JwtException exception) {
      logger.warn("Google id token verification failed: {}", exception.getMessage());
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google token.");
    }
  }
}
