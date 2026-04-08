package hr.kronos.backend.auth.oauth;

import java.util.List;
import java.util.Set;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public class AudienceValidator implements OAuth2TokenValidator<Jwt> {
  private static final OAuth2Error ERROR =
      new OAuth2Error("invalid_token", "Invalid token audience.", null);

  private final Set<String> allowedAudiences;

  public AudienceValidator(Set<String> allowedAudiences) {
    this.allowedAudiences = allowedAudiences;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    List<String> audiences = token.getAudience();
    boolean matched = audiences != null && audiences.stream().anyMatch(allowedAudiences::contains);
    return matched ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(ERROR);
  }
}
