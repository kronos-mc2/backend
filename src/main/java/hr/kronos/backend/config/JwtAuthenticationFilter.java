package hr.kronos.backend.config;

import hr.kronos.backend.auth.AuthPrincipal;
import hr.kronos.backend.auth.JwtService;
import hr.kronos.backend.auth.persistence.AuthMapper;
import hr.kronos.backend.auth.persistence.UserRow;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final AuthMapper authMapper;

  public JwtAuthenticationFilter(JwtService jwtService, AuthMapper authMapper) {
    this.jwtService = jwtService;
    this.authMapper = authMapper;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getServletPath();
    return "/api/auth/register".equals(path)
        || "/api/auth/login".equals(path)
        || "/api/auth/google".equals(path)
        || "/api/auth/apple".equals(path);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String authorizationHeader = request.getHeader("Authorization");

    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
      String token = authorizationHeader.substring(7);

      try {
        Claims claims = jwtService.parseClaims(token);
        String userId = claims.getSubject();
        String email = claims.get("email", String.class);
        String name = claims.get("name", String.class);

        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
          UserRow user = authMapper.findById(userId);
          if (user == null || !isActive(user)) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
          }
          AuthPrincipal principal = new AuthPrincipal(userId, email, name);
          UsernamePasswordAuthenticationToken authentication =
              new UsernamePasswordAuthenticationToken(
                  principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
          SecurityContextHolder.getContext().setAuthentication(authentication);
        }
      } catch (Exception _) {
        SecurityContextHolder.clearContext();
      }
    }

    filterChain.doFilter(request, response);
  }

  private boolean isActive(UserRow user) {
    String status = user.getAccountStatus();
    return status == null || status.isBlank() || "active".equals(status);
  }
}
