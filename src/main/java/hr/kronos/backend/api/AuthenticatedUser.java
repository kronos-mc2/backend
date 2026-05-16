package hr.kronos.backend.api;

import hr.kronos.backend.auth.AuthPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

final class AuthenticatedUser {
  private AuthenticatedUser() {}

  static String userId(Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
      throw new AccessDeniedException("Authenticated user is required");
    }

    return principal.userId();
  }
}
