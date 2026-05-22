package hr.kronos.backend.config;

import hr.kronos.backend.auth.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DevHttpRequestLoggingFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(DevHttpRequestLoggingFilter.class);
  private final JwtService jwtService;

  public DevHttpRequestLoggingFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startedAt = System.currentTimeMillis();

    String method = request.getMethod();
    String pathWithQuery = buildPathWithQuery(request);
    String clientIp = resolveClientIp(request);
    String userAgent = shorten(request.getHeader("User-Agent"), 160);
    String user = resolveUser(request);

    log.info("HTTP IN  method={} path={} user={} ip={} ua={}", method, pathWithQuery, user, clientIp, userAgent);

    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = System.currentTimeMillis() - startedAt;
      log.info(
          "HTTP OUT method={} path={} user={} status={} durationMs={}",
          method,
          pathWithQuery,
          user,
          response.getStatus(),
          durationMs);
    }
  }

  private String buildPathWithQuery(HttpServletRequest request) {
    String query = request.getQueryString();
    if (query == null || query.isBlank()) {
      return request.getRequestURI();
    }
    return request.getRequestURI() + "?" + query;
  }

  private String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String resolveUser(HttpServletRequest request) {
    String authorizationHeader = request.getHeader("Authorization");
    if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
      return "anonymous";
    }

    try {
      Claims claims = jwtService.parseClaims(authorizationHeader.substring(7));
      String userId = claims.getSubject();
      String email = claims.get("email", String.class);
      if (userId == null || userId.isBlank()) {
        return "invalid-token";
      }
      if (email == null || email.isBlank()) {
        return userId;
      }
      return userId + "/" + email;
    } catch (Exception _) {
      return "invalid-token";
    }
  }

  private String shorten(String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return "-";
    }
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...";
  }
}
