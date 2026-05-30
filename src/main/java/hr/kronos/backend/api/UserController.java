package hr.kronos.backend.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hr.kronos.backend.api.dto.ChangePasswordRequest;
import hr.kronos.backend.api.dto.DeleteAccountRequest;
import hr.kronos.backend.api.dto.ProfileActivityDto;
import hr.kronos.backend.api.dto.TransactionDto;
import hr.kronos.backend.api.dto.UpdateProfileRequest;
import hr.kronos.backend.auth.dto.AuthUserDto;
import hr.kronos.backend.profile.ProfileService;
import hr.kronos.backend.storage.StoredObjectContent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class UserController {
  private final ProfileService profileService;
  private final ObjectMapper objectMapper;

  public UserController(ProfileService profileService, ObjectMapper objectMapper) {
    this.profileService = profileService;
    this.objectMapper = objectMapper;
  }

  @PatchMapping("/users/me/profile")
  public AuthUserDto updateProfile(@RequestBody UpdateProfileRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return profileService.updateProfile(request, userId);
  }

  @PatchMapping(value = "/users/me/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AuthUserDto updateProfileWithAvatar(
      @RequestPart("profile") String profileJson,
      @RequestPart(value = "avatar", required = false) MultipartFile avatar,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return profileService.updateProfile(parseUpdateProfileRequest(profileJson), avatar, userId);
  }

  @GetMapping("/users/{id}/avatar/content")
  public ResponseEntity<byte[]> getAvatarContent(@PathVariable String id) {
    StoredObjectContent content = profileService.getAvatarContent(id);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .contentLength(content.contentLength())
        .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .body(content.bytes());
  }

  @PostMapping("/users/me/password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(@RequestBody ChangePasswordRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    profileService.changePassword(request, userId);
  }

  @DeleteMapping("/users/me/account")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteAccount(@RequestBody(required = false) DeleteAccountRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    profileService.requestAccountDeletion(request, userId);
  }

  @GetMapping("/users/me/activity")
  public ProfileActivityDto getActivity(Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return profileService.getActivity(userId);
  }

  @GetMapping("/users/me/transactions")
  public List<TransactionDto> getTransactions(Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return profileService.getTransactions(userId);
  }

  private UpdateProfileRequest parseUpdateProfileRequest(String profileJson) {
    try {
      return objectMapper.readValue(profileJson, UpdateProfileRequest.class);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid profile payload.", exception);
    }
  }
}
