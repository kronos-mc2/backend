package hr.kronos.backend.auth;

import hr.kronos.backend.auth.dto.AuthResponse;
import hr.kronos.backend.auth.dto.AuthUserDto;
import hr.kronos.backend.auth.dto.LoginRequest;
import hr.kronos.backend.auth.dto.RegisterRequest;
import hr.kronos.backend.auth.dto.SocialLoginRequest;
import hr.kronos.backend.auth.oauth.AppleIdTokenVerifierService;
import hr.kronos.backend.auth.oauth.GoogleIdTokenVerifierService;
import hr.kronos.backend.auth.persistence.AuthMapper;
import hr.kronos.backend.auth.persistence.UserRow;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  private final AuthMapper authMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final GoogleIdTokenVerifierService googleIdTokenVerifierService;
  private final AppleIdTokenVerifierService appleIdTokenVerifierService;

  public AuthService(
      AuthMapper authMapper,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      GoogleIdTokenVerifierService googleIdTokenVerifierService,
      AppleIdTokenVerifierService appleIdTokenVerifierService) {
    this.authMapper = authMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.googleIdTokenVerifierService = googleIdTokenVerifierService;
    this.appleIdTokenVerifierService = appleIdTokenVerifierService;
  }

  public AuthResponse register(RegisterRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    String email = normalizeEmail(request.email());
    String fullName = normalizeName(request.name());
    String password = request.password();

    if (!isValidEmail(email)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email.");
    }

    if (!PasswordPolicy.isValid(password)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Password must have at least 8 chars, uppercase, lowercase, digit and special char.");
    }

    if (authMapper.findByEmail(email) != null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists.");
    }

    UserRow user = new UserRow();
    user.setId(nextUserId());
    user.setEmail(email);
    user.setFullName(fullName);
    user.setAuthProvider("local");
    user.setPasswordHash(passwordEncoder.encode(password));
    try {
      authMapper.insert(user);
    } catch (DuplicateKeyException _) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists.");
    }

    return buildAuthResponse(user);
  }

  public AuthResponse login(LoginRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    String email = normalizeEmail(request.email());
    String password = request.password();

    UserRow user = authMapper.findByEmail(email);
    if (user == null || user.getPasswordHash() == null || password == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }

    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }

    return buildAuthResponse(user);
  }

  public AuthResponse loginWithGoogle(SocialLoginRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    SocialIdentity identity = googleIdTokenVerifierService.verify(request.idToken());
    return loginOrCreateSocial(identity, "google", null);
  }

  public AuthResponse loginWithApple(SocialLoginRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    SocialIdentity identity = appleIdTokenVerifierService.verify(request.idToken());
    return loginOrCreateSocial(identity, "apple", request.name());
  }

  public AuthUserDto getUserProfile(String userId) {
    UserRow user = authMapper.findById(userId);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found.");
    }
    return new AuthUserDto(user.getFullName(), user.getEmail(), user.getBio(), user.getAvatarUrl());
  }

  private AuthResponse loginOrCreateSocial(SocialIdentity identity, String provider, String fallbackName) {
    String providerSubject = normalizeProviderSubject(identity.providerSubject());
    String email = normalizeEmail(identity.email());
    if (!isValidEmail(email)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Social token email is invalid.");
    }

    String name = normalizeNameOrNull(identity.name());
    if (name == null) {
      name = normalizeNameOrNull(fallbackName);
    }
    if (name == null) {
      name = buildDefaultNameFromEmail(email);
    }

    UserRow user = authMapper.findBySocialIdentity(provider, providerSubject);
    if (user != null) {
      authMapper.updateSocialIdentityEmail(provider, providerSubject, email);
      updateSocialNameIfNeeded(user, name);
      return buildAuthResponse(user);
    }

    user = authMapper.findByEmail(email);
    if (user == null) {
      user = new UserRow();
      user.setId(nextUserId());
      user.setEmail(email);
      user.setFullName(name);
      user.setAuthProvider(provider);
      user.setPasswordHash(null);
      try {
        authMapper.insert(user);
      } catch (DuplicateKeyException _) {
        user = authMapper.findByEmail(email);
        if (user == null) {
          throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists.");
        }
      }
    } else {
      updateSocialNameIfNeeded(user, name);
    }

    user = linkSocialIdentityOrReload(user, provider, providerSubject, email);
    return buildAuthResponse(user);
  }

  private UserRow linkSocialIdentityOrReload(UserRow user, String provider, String providerSubject, String email) {
    try {
      int inserted =
          authMapper.insertSocialIdentity(nextSocialIdentityId(), user.getId(), provider, providerSubject, email);
      if (inserted == 0) {
        UserRow linkedUser = authMapper.findBySocialIdentity(provider, providerSubject);
        if (linkedUser != null) {
          return linkedUser;
        }
      }
    } catch (DuplicateKeyException _) {
      UserRow linkedUser = authMapper.findBySocialIdentity(provider, providerSubject);
      if (linkedUser != null) {
        return linkedUser;
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Social account is already linked.");
    }
    return user;
  }

  private void updateSocialNameIfNeeded(UserRow user, String name) {
    if (!name.equals(user.getFullName())) {
      authMapper.updateName(user.getId(), name);
      user.setFullName(name);
    }
  }

  private AuthResponse buildAuthResponse(UserRow user) {
    String token = jwtService.createToken(user);
    AuthUserDto userDto = new AuthUserDto(user.getFullName(), user.getEmail(), user.getBio(), user.getAvatarUrl());
    return new AuthResponse(token, userDto);
  }

  private String normalizeEmail(String email) {
    if (email == null) {
      return "";
    }
    return email.trim().toLowerCase();
  }

  private String normalizeName(String name) {
    String normalized = normalizeNameOrNull(name);
    if (normalized == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
    }
    return normalized;
  }

  private String normalizeNameOrNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.isBlank() ? null : normalized;
  }

  private String normalizeProviderSubject(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Social token subject is invalid.");
    }
    return normalized;
  }

  private boolean isValidEmail(String email) {
    return EMAIL_PATTERN.matcher(email).matches();
  }

  private String nextUserId() {
    return "usr_" + UUID.randomUUID().toString().replace("-", "");
  }

  private String nextSocialIdentityId() {
    return "sid_" + UUID.randomUUID().toString().replace("-", "");
  }

  private String buildDefaultNameFromEmail(String email) {
    int atIndex = email.indexOf('@');
    if (atIndex <= 0) {
      return "User";
    }
    return email.substring(0, atIndex);
  }
}
