package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CoordinatesDto;
import hr.kronos.backend.api.dto.EventMediaDto;
import hr.kronos.backend.api.dto.EventParticipantDto;
import hr.kronos.backend.api.dto.FeedPreferenceDto;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.events.persistence.EventMediaRow;
import hr.kronos.backend.events.persistence.EventParticipantRow;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.events.persistence.FeedPreferenceRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class EventDtoMapper {
  private static final String MEDIA_TYPE_VIDEO = "video";

  private final EventMediaStorageService mediaStorageService;

  public EventDtoMapper(EventMediaStorageService mediaStorageService) {
    this.mediaStorageService = mediaStorageService;
  }

  public AppEventDto toDto(EventRow row) {
    return toDto(row, null);
  }

  public AppEventDto toDto(EventRow row, List<EventMediaDto> media) {
    OffsetDateTime startAt = row.getStartAt() == null ? row.getWhenIso() : row.getStartAt();

    CoordinatesDto entranceCoordinates = null;
    if (row.getEntranceLatitude() != null && row.getEntranceLongitude() != null) {
      entranceCoordinates = new CoordinatesDto(row.getEntranceLatitude(), row.getEntranceLongitude());
    }

    LocalizedTextDto entryInstructions = null;
    if (row.getEntryInstructionsHr() != null && row.getEntryInstructionsEn() != null) {
      entryInstructions = new LocalizedTextDto(row.getEntryInstructionsHr(), row.getEntryInstructionsEn());
    }

    return new AppEventDto(
        row.getId(),
        row.getCreatorUserId(),
        row.getCreatorName(),
        row.getCreatorAvatarUrl(),
        new LocalizedTextDto(row.getTitleHr(), row.getTitleEn()),
        new LocalizedTextDto(row.getWhereHr(), row.getWhereEn()),
        row.getAddress(),
        new LocalizedTextDto(row.getAboutHr(), row.getAboutEn()),
        timestamp(startAt),
        timestamp(startAt),
        timestamp(row.getEndAt()),
        row.getEventType(),
        new CoordinatesDto(row.getLatitude(), row.getLongitude()),
        entranceCoordinates,
        entryInstructions,
        row.getVisibility(),
        row.getAttendanceMode(),
        row.getPriceAmount(),
        row.getPriceCurrency(),
        row.getCapacity(),
        row.getStatus(),
        row.getSourceUrl(),
        timestamp(row.getUpdatedAt()),
        eventCacheVersion(row),
        row.getEventRatingAverage() == null ? BigDecimal.ZERO : row.getEventRatingAverage(),
        row.getEventRatingCount(),
        row.getOrganizerRatingAverage() == null ? BigDecimal.ZERO : row.getOrganizerRatingAverage(),
        row.getOrganizerRatingCount(),
        row.getLikeCount(),
        Boolean.TRUE.equals(row.getLikedByMe()),
        row.getParticipantCount(),
        row.getWaitlistCount(),
        EventRules.isJoinedByMe(row.getUserParticipantStatus()),
        row.getUserParticipantStatus(),
        EventRules.canJoin(row),
        tagsFromRow(row),
        media == null || media.isEmpty() ? null : media);
  }

  public EventMediaDto toMediaDto(EventMediaRow row) {
    String url = mediaUrl(row, row.getUrl());
    String thumbnailUrl = thumbnailUrl(row);
    return new EventMediaDto(
        row.getId(),
        row.getMediaType(),
        url,
        thumbnailUrl,
        row.getOriginalFilename(),
        row.getByteSize(),
        row.getWidth(),
        row.getHeight(),
        row.getSortOrder());
  }

  public FeedPreferenceDto toFeedPreferenceDto(FeedPreferenceRow row) {
    return new FeedPreferenceDto(
        row.getId(),
        row.getBlockType(),
        row.getTargetId(),
        row.getTargetLabel(),
        timestamp(row.getCreatedAt()));
  }

  public EventParticipantDto toParticipantDto(EventParticipantRow row) {
    return new EventParticipantDto(
        row.getUserId(),
        row.getFullName(),
        row.getAvatarUrl(),
        row.getStatus(),
        timestamp(row.getJoinedAt()),
        timestamp(row.getApprovedAt()),
        Boolean.TRUE.equals(row.getBlocked()));
  }

  public String eventCacheVersion(EventRow row) {
    return row.getUpdatedAt() == null ? row.getId() : row.getId() + ":" + row.getUpdatedAt().toInstant().toEpochMilli();
  }

  public List<String> tagsFromRow(EventRow row) {
    String tagsCsv = trimToNull(row.getTagsCsv());
    if (tagsCsv == null) {
      return List.of();
    }

    return List.of(tagsCsv.split(",")).stream().map(this::trimToNull).filter(Objects::nonNull).toList();
  }

  public String timestamp(OffsetDateTime value) {
    return value == null ? null : value.toInstant().toString();
  }

  private String thumbnailUrl(EventMediaRow row) {
    if (trimToNull(row.getThumbnailStorageKey()) != null) {
      return "/api/events/" + row.getEventId() + "/media/" + row.getId() + "/thumbnail";
    }
    return mediaUrl(row, firstNonBlank(row.getThumbnailUrl(), row.getUrl()));
  }

  private String mediaUrl(EventMediaRow row, String fallbackUrl) {
    if (trimToNull(row.getStorageKey()) != null) {
      String contentPath = "/api/events/" + row.getEventId() + "/media/" + row.getId() + "/content";
      return MEDIA_TYPE_VIDEO.equals(row.getMediaType())
          ? contentPath + "/" + mediaStorageService.mediaContentFilename(row)
          : contentPath;
    }
    return fallbackUrl;
  }

  private String firstNonBlank(String primary, String fallback) {
    String normalizedPrimary = trimToNull(primary);
    return normalizedPrimary != null ? normalizedPrimary : fallback;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
