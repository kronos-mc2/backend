package hr.kronos.backend.profile;

import hr.kronos.backend.api.dto.AppNotificationDto;
import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.ChangePasswordRequest;
import hr.kronos.backend.api.dto.DeleteAccountRequest;
import hr.kronos.backend.api.dto.ProfileActivityDto;
import hr.kronos.backend.api.dto.TransactionDto;
import hr.kronos.backend.api.dto.UpdateProfileRequest;
import hr.kronos.backend.auth.PasswordPolicy;
import hr.kronos.backend.auth.dto.AuthUserDto;
import hr.kronos.backend.auth.persistence.AuthMapper;
import hr.kronos.backend.auth.persistence.UserRow;
import hr.kronos.backend.events.EventService;
import hr.kronos.backend.profile.persistence.ProfileMapper;
import hr.kronos.backend.profile.persistence.NotificationRow;
import hr.kronos.backend.profile.persistence.TransactionRow;
import hr.kronos.backend.storage.ObjectStorageProperties;
import hr.kronos.backend.storage.ObjectStorageService;
import hr.kronos.backend.storage.StoredObject;
import hr.kronos.backend.storage.StoredObjectContent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {
  private static final int MAX_NAME_LENGTH = 160;
  private static final int MAX_BIO_LENGTH = 280;
  private static final int MAX_AVATAR_URL_LENGTH = 1000;
  private static final int ACCOUNT_RECOVERY_DAYS = 30;
  private static final String CONTENT_TYPE_JPEG = "image/jpeg";
  private static final String CONTENT_TYPE_PNG = "image/png";

  private final AuthMapper authMapper;
  private final EventService eventService;
  private final ProfileMapper profileMapper;
  private final ObjectStorageService objectStorageService;
  private final ObjectStorageProperties objectStorageProperties;
  private final PasswordEncoder passwordEncoder;

  public ProfileService(
      AuthMapper authMapper,
      EventService eventService,
      ProfileMapper profileMapper,
      ObjectStorageService objectStorageService,
      ObjectStorageProperties objectStorageProperties,
      PasswordEncoder passwordEncoder) {
    this.authMapper = authMapper;
    this.eventService = eventService;
    this.profileMapper = profileMapper;
    this.objectStorageService = objectStorageService;
    this.objectStorageProperties = objectStorageProperties;
    this.passwordEncoder = passwordEncoder;
  }

  public AuthUserDto updateProfile(UpdateProfileRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    UserRow user = requireUser(userId);
    String name = normalizeName(request.name());
    String bio = normalizeOptionalText(request.bio(), MAX_BIO_LENGTH, "bio");
    String avatarUrl = normalizeAvatarUrl(request.avatarUrl(), user);

    authMapper.updateProfile(user.getId(), name, bio, avatarUrl);
    return new AuthUserDto(name, user.getEmail(), bio, avatarUrl);
  }

  public AuthUserDto updateProfile(UpdateProfileRequest request, MultipartFile avatar, String userId) {
    AuthUserDto updated = updateProfile(request, userId);
    if (avatar == null || avatar.isEmpty()) {
      return updated;
    }

    UserRow user = requireUser(userId);
    StoredObject avatarObject = storeAvatar(user.getId(), avatar);
    deleteStoredAvatar(user);
    String avatarUrl = "/api/users/" + user.getId() + "/avatar/content?v=" + UUID.randomUUID().toString().replace("-", "");
    authMapper.updateProfileAvatar(
        user.getId(),
        avatarUrl,
        avatarObject.storageKey(),
        avatarObject.bucketName(),
        avatarObject.contentType(),
        avatarObject.byteSize());
    return new AuthUserDto(updated.name(), updated.email(), updated.bio(), avatarUrl);
  }

  public StoredObjectContent getAvatarContent(String userId) {
    UserRow user = authMapper.findById(userId);
    if (user == null
        || trimToNull(user.getAvatarBucketName()) == null
        || trimToNull(user.getAvatarStorageKey()) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar not found.");
    }
    StoredObjectContent content = objectStorageService.get(user.getAvatarBucketName(), user.getAvatarStorageKey());
    String contentType = firstNonBlank(user.getAvatarContentType(), content.contentType(), CONTENT_TYPE_JPEG);
    return new StoredObjectContent(content.bytes(), contentType, content.contentLength());
  }

  public void changePassword(ChangePasswordRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }
    UserRow user = requireUser(userId);
    if (user.getPasswordHash() == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Password login is not enabled for this account.");
    }
    if (request.currentPassword() == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is invalid.");
    }
    if (!PasswordPolicy.isValid(request.newPassword())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Password must have at least 8 chars, uppercase, lowercase, digit and special char.");
    }
    authMapper.updatePassword(user.getId(), passwordEncoder.encode(request.newPassword()));
  }

  public void requestAccountDeletion(DeleteAccountRequest request, String userId) {
    UserRow user = requireUser(userId);
    if (user.getPasswordHash() != null) {
      String currentPassword = request == null ? null : request.currentPassword();
      if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is invalid.");
      }
    }

    OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(ACCOUNT_RECOVERY_DAYS);
    authMapper.insertDeletionRecovery(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getBio(),
        user.getAvatarStorageKey() == null ? user.getAvatarUrl() : null,
        user.getPasswordHash(),
        user.getAuthProvider(),
        scheduledAt);
    String anonymizedEmail = "deleted+" + user.getId() + "@deleted.gdjeikada.local";
    authMapper.markDeletionRequested(user.getId(), anonymizedEmail, scheduledAt);
    authMapper.anonymizeSocialIdentitiesForDeletion(user.getId(), anonymizedEmail);
    authMapper.deletePersonalStateForDeletion(user.getId());
    deleteStoredAvatar(user);
  }

  public ProfileActivityDto getActivity(String userId) {
    List<AppEventDto> joinedEvents = eventService.getMyEvents(userId, "joined");
    List<AppEventDto> likedEvents = eventService.getLikedEvents(userId);
    List<AppEventDto> ratingCandidates = profileMapper.findRatingCandidates(userId).stream().map(eventService::toDto).toList();
    return new ProfileActivityDto(
        joinedEvents,
        likedEvents,
        ratingCandidates,
        getTransactions(userId),
        profileMapper.findNotificationsForUser(userId).stream().map(this::toDto).toList());
  }

  public List<TransactionDto> getTransactions(String userId) {
    return profileMapper.findTransactionsForUser(userId).stream().map(this::toDto).toList();
  }

  private UserRow requireUser(String userId) {
    UserRow user = authMapper.findById(userId);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found.");
    }
    if (user.getAccountStatus() != null && !"active".equals(user.getAccountStatus())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account is deleted.");
    }
    return user;
  }

  private TransactionDto toDto(TransactionRow row) {
    return new TransactionDto(
        row.getId(),
        row.getEventId(),
        row.getEventTitle(),
        row.getOrderId(),
        row.getTransactionType(),
        row.getAmount(),
        row.getCurrency(),
        row.getStatus(),
        row.getDescription(),
        row.getPaymentProvider(),
        row.getProviderReference(),
        row.getCreatedAt() == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(row.getCreatedAt()));
  }

  private AppNotificationDto toDto(NotificationRow row) {
    return new AppNotificationDto(
        row.getId(),
        row.getNotificationType(),
        row.getTitle(),
        row.getBody(),
        row.getEventId(),
        row.getCreatedAt() == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(row.getCreatedAt()),
        row.getReadAt() == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(row.getReadAt()));
  }

  private String normalizeName(String name) {
    String normalized = normalizeOptionalText(name, MAX_NAME_LENGTH, "name");
    if (normalized == null || normalized.length() < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
    }
    return normalized;
  }

  private String normalizeAvatarUrl(String avatarUrl, UserRow user) {
    String normalized = normalizeOptionalText(avatarUrl, MAX_AVATAR_URL_LENGTH, "avatarUrl");
    if (normalized == null) {
      return null;
    }

    // Uploaded avatars use an authenticated relative URL generated by this service.
    if (normalized.equals(user.getAvatarUrl())
        && normalized.startsWith("/api/users/" + user.getId() + "/avatar/content?v=")) {
      return normalized;
    }

    String lower = normalized.toLowerCase();
    if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatarUrl must be an absolute URL.");
    }
    return normalized;
  }

  private StoredObject storeAvatar(String userId, MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar image is required.");
    }
    try {
      byte[] bytes = file.getBytes();
      validateAvatarFileSize(bytes.length);
      String contentType = normalizeImageContentType(file.getContentType(), file.getOriginalFilename());
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar must be a readable JPEG or PNG file.");
      }
      return objectStorageService.putImage(
          buildAvatarStorageKey(userId, contentType),
          bytes,
          contentType,
          image.getWidth(),
          image.getHeight());
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar image could not be read.", exception);
    }
  }

  private void validateAvatarFileSize(int byteSize) {
    if (byteSize <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar image is empty.");
    }
    if (byteSize > objectStorageProperties.getMaxFileBytes()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar image must be 5 MB or smaller.");
    }
  }

  private String normalizeImageContentType(String contentType, String filename) {
    String normalized = trimToNull(contentType);
    if (CONTENT_TYPE_JPEG.equalsIgnoreCase(normalized) || "image/jpg".equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_JPEG;
    }
    if (CONTENT_TYPE_PNG.equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_PNG;
    }
    String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
      return CONTENT_TYPE_JPEG;
    }
    if (lowerFilename.endsWith(".png")) {
      return CONTENT_TYPE_PNG;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatar image must be JPEG or PNG.");
  }

  private String buildAvatarStorageKey(String userId, String contentType) {
    String extension = CONTENT_TYPE_PNG.equals(contentType) ? "png" : "jpg";
    return "users/" + userId + "/avatar/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
  }

  private void deleteStoredAvatar(UserRow user) {
    if (user == null || trimToNull(user.getAvatarBucketName()) == null || trimToNull(user.getAvatarStorageKey()) == null) {
      return;
    }
    objectStorageService.delete(user.getAvatarBucketName(), user.getAvatarStorageKey());
  }

  private String normalizeOptionalText(String value, int maxLength, String fieldName) {
    if (value == null) {
      return null;
    }

    String normalized = value.trim().replaceAll("\\s+", " ");
    if (normalized.isBlank()) {
      return null;
    }
    if (normalized.length() > maxLength) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is too long.");
    }
    return normalized;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      String normalized = trimToNull(value);
      if (normalized != null) {
        return normalized;
      }
    }
    return null;
  }
}
