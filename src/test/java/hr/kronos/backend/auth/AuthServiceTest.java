package hr.kronos.backend.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.kronos.backend.auth.dto.LoginRequest;
import hr.kronos.backend.auth.dto.RegisterRequest;
import hr.kronos.backend.auth.oauth.AppleIdTokenVerifierService;
import hr.kronos.backend.auth.oauth.GoogleIdTokenVerifierService;
import hr.kronos.backend.auth.persistence.AuthMapper;
import hr.kronos.backend.auth.persistence.UserRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  @Mock
  private AuthMapper authMapper;
  @Mock
  private PasswordEncoder passwordEncoder;
  @Mock
  private JwtService jwtService;
  @Mock
  private GoogleIdTokenVerifierService googleIdTokenVerifierService;
  @Mock
  private AppleIdTokenVerifierService appleIdTokenVerifierService;

  @InjectMocks
  private AuthService authService;

  @Test
  void registerRejectsInvalidEmail() {
    RegisterRequest request = new RegisterRequest("User", "not-an-email", "Valid123!");

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> authService.register(request));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    verify(authMapper, never()).insert(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void registerRejectsDuplicateEmail() {
    UserRow existing = new UserRow();
    existing.setId("usr_existing");
    when(authMapper.findByEmail("user@example.com")).thenReturn(existing);
    RegisterRequest request = new RegisterRequest("User", "user@example.com", "Valid123!");

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> authService.register(request));

    assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    verify(authMapper, never()).insert(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void loginRejectsUnknownEmail() {
    when(authMapper.findByEmail("missing@example.com")).thenReturn(null);
    when(authMapper.findDeletionRecoveryByEmail("missing@example.com")).thenReturn(null);
    LoginRequest request = new LoginRequest("missing@example.com", "Valid123!");

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> authService.login(request));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }
}
