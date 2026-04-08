package hr.kronos.backend.api;

import hr.kronos.backend.auth.AuthPrincipal;
import hr.kronos.backend.auth.AuthService;
import hr.kronos.backend.auth.dto.AuthResponse;
import hr.kronos.backend.auth.dto.AuthUserDto;
import hr.kronos.backend.auth.dto.LoginRequest;
import hr.kronos.backend.auth.dto.RegisterRequest;
import hr.kronos.backend.auth.dto.SocialLoginRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/register")
  public AuthResponse register(@RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  @PostMapping("/login")
  public AuthResponse login(@RequestBody LoginRequest request) {
    return authService.login(request);
  }

  @PostMapping("/google")
  public AuthResponse loginWithGoogle(@RequestBody SocialLoginRequest request) {
    return authService.loginWithGoogle(request);
  }

  @PostMapping("/apple")
  public AuthResponse loginWithApple(@RequestBody SocialLoginRequest request) {
    return authService.loginWithApple(request);
  }

  @GetMapping("/me")
  public AuthUserDto me(Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return authService.getUserProfile(principal.userId());
  }
}
