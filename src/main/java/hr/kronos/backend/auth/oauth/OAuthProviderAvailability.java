package hr.kronos.backend.auth.oauth;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.springframework.security.oauth2.jwt.JwtException;

final class OAuthProviderAvailability {
  private OAuthProviderAvailability() {}

  static boolean isUnavailable(JwtException exception, String jwksHost) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof UnknownHostException
          || current instanceof SocketTimeoutException
          || current instanceof IOException) {
        return true;
      }
      current = current.getCause();
    }

    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return false;
    }

    String normalizedMessage = message.toLowerCase();
    return normalizedMessage.contains("i/o error")
        || normalizedMessage.contains("timed out")
        || normalizedMessage.contains("unknownhost")
        || normalizedMessage.contains(jwksHost.toLowerCase());
  }
}
