package hr.kronos.backend.profile;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.ProfileActivityDto;
import hr.kronos.backend.api.dto.TransactionDto;
import hr.kronos.backend.api.dto.UpdateProfileRequest;
import hr.kronos.backend.auth.dto.AuthUserDto;
import hr.kronos.backend.auth.persistence.AuthMapper;
import hr.kronos.backend.auth.persistence.UserRow;
import hr.kronos.backend.events.EventService;
import hr.kronos.backend.profile.persistence.ProfileMapper;
import hr.kronos.backend.profile.persistence.TransactionRow;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {
  private static final int MAX_NAME_LENGTH = 160;
  private static final int MAX_BIO_LENGTH = 280;
  private static final int MAX_AVATAR_URL_LENGTH = 1000;

  private final AuthMapper authMapper;
  private final EventService eventService;
  private final ProfileMapper profileMapper;

  public ProfileService(AuthMapper authMapper, EventService eventService, ProfileMapper profileMapper) {
    this.authMapper = authMapper;
    this.eventService = eventService;
    this.profileMapper = profileMapper;
  }

  public AuthUserDto updateProfile(UpdateProfileRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    UserRow user = requireUser(userId);
    String name = normalizeName(request.name());
    String bio = normalizeOptionalText(request.bio(), MAX_BIO_LENGTH, "bio");
    String avatarUrl = normalizeAvatarUrl(request.avatarUrl());

    authMapper.updateProfile(user.getId(), name, bio, avatarUrl);
    return new AuthUserDto(name, user.getEmail(), bio, avatarUrl);
  }

  public ProfileActivityDto getActivity(String userId) {
    List<AppEventDto> joinedEvents = eventService.getMyEvents(userId, "joined");
    List<AppEventDto> likedEvents = eventService.getLikedEvents(userId);
    List<AppEventDto> ratingCandidates = profileMapper.findRatingCandidates(userId).stream().map(eventService::toDto).toList();
    return new ProfileActivityDto(joinedEvents, likedEvents, ratingCandidates, getTransactions(userId));
  }

  public List<TransactionDto> getTransactions(String userId) {
    return profileMapper.findTransactionsForUser(userId).stream().map(this::toDto).toList();
  }

  private UserRow requireUser(String userId) {
    UserRow user = authMapper.findById(userId);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found.");
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

  private String normalizeName(String name) {
    String normalized = normalizeOptionalText(name, MAX_NAME_LENGTH, "name");
    if (normalized == null || normalized.length() < 2) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required.");
    }
    return normalized;
  }

  private String normalizeAvatarUrl(String avatarUrl) {
    String normalized = normalizeOptionalText(avatarUrl, MAX_AVATAR_URL_LENGTH, "avatarUrl");
    if (normalized == null) {
      return null;
    }

    String lower = normalized.toLowerCase();
    if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatarUrl must be an absolute URL.");
    }
    return normalized;
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
}
