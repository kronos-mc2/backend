package hr.kronos.backend.events;

import hr.kronos.backend.events.persistence.EventRow;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EventFeedRequestNormalizer {
  private static final String DEFAULT_ATTENDANCE_MODE = "open";
  private static final String ATTENDANCE_MODE_PAID = "paid";
  private static final String ATTENDANCE_MODE_WAITLIST = "waitlist";
  private static final int DEFAULT_FEED_LIMIT = 5;
  private static final int MAX_FEED_LIMIT = 10;
  private static final int MAX_FEED_SEED_LENGTH = 80;
  private static final int MAX_FEED_EXCLUDED_EVENT_IDS = 200;
  private static final String FEED_SCOPE_ALL = "all";
  private static final String FEED_SCOPE_FRIENDS = "friends";
  private static final String FEED_SORT_DEFAULT = "default";
  private static final String FEED_SORT_TRENDING = "trending";

  public FeedFilters normalizeFeedFilters(
      String excludeEventIds,
      String from,
      String to,
      Double lat,
      Double lng,
      Double radiusKm,
      String query,
      String tags,
      String attendanceModes,
      String scope,
      String sort) {
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime fromDate = parseOptionalWhenIso(from, "from");
    OffsetDateTime toDate = parseOptionalWhenIso(to, "to");
    if (fromDate == null || fromDate.isBefore(now)) {
      fromDate = now;
    }

    if (toDate != null && toDate.isBefore(fromDate)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "to must be after from.");
    }

    if ((lat == null) != (lng == null)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat and lng must be provided together.");
    }

    if (lat != null && lng != null && !isValidCoordinate(lat, lng)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat/lng are invalid.");
    }

    if (radiusKm != null && (radiusKm <= 0 || radiusKm > 500)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "radiusKm must be between 0 and 500.");
    }

    return new FeedFilters(
        normalizeExcludedEventIds(excludeEventIds),
        fromDate,
        toDate,
        lat,
        lng,
        radiusKm,
        trimToNull(query),
        normalizeFilterTags(tags),
        normalizeAttendanceModeFilter(attendanceModes),
        normalizeFeedScope(scope),
        normalizeFeedSort(sort));
  }

  public List<String> normalizeFilterTags(String rawTags) {
    String normalizedRawTags = trimToNull(rawTags);
    if (normalizedRawTags == null) {
      return List.of();
    }

    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String rawTag : normalizedRawTags.split(",")) {
      EventTagCatalog.TagDefinition tag = normalizeTag(rawTag);
      if (tag != null) {
        normalized.add(tag.tag().toLowerCase(Locale.ROOT));
      }
    }
    return new ArrayList<>(normalized);
  }

  public List<String> normalizeAttendanceModeFilter(String rawAttendanceModes) {
    String normalizedRawAttendanceModes = trimToNull(rawAttendanceModes);
    if (normalizedRawAttendanceModes == null) {
      return List.of();
    }

    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String rawAttendanceMode : normalizedRawAttendanceModes.split(",")) {
      if (trimToNull(rawAttendanceMode) != null) {
        normalized.add(normalizeAttendanceModeFilterValue(rawAttendanceMode));
      }
    }
    return new ArrayList<>(normalized);
  }

  public int normalizeFeedLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_FEED_LIMIT;
    }

    if (limit < 1 || limit > MAX_FEED_LIMIT) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_FEED_LIMIT + ".");
    }

    return limit;
  }

  public FeedCursor parseFeedCursor(String cursor) {
    String normalized = trimToNull(cursor);
    if (normalized == null) {
      return new FeedCursor(0, List.of());
    }

    try {
      String decoded = new String(Base64.getUrlDecoder().decode(normalized), StandardCharsets.UTF_8);
      if (decoded.startsWith("v2:")) {
        return new FeedCursor(0, normalizeExcludedEventIds(decoded.substring(3)));
      }

      int offset = Integer.parseInt(decoded);
      if (offset < 0) {
        throw new IllegalArgumentException("Invalid feed cursor.");
      }
      return new FeedCursor(offset, List.of());
    } catch (IllegalArgumentException _) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor format.");
    }
  }

  public List<String> mergeExcludedEventIds(List<String> first, List<String> second) {
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    if (first != null) {
      normalized.addAll(first);
    }
    if (second != null) {
      normalized.addAll(second);
    }

    if (normalized.size() > MAX_FEED_EXCLUDED_EVENT_IDS) {
      return normalized.stream().skip((long) normalized.size() - MAX_FEED_EXCLUDED_EVENT_IDS).toList();
    }

    return new ArrayList<>(normalized);
  }

  public List<String> appendReturnedEventIds(List<String> excludedEventIds, List<EventRow> returnedRows) {
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    if (excludedEventIds != null) {
      normalized.addAll(excludedEventIds);
    }
    returnedRows.stream().map(EventRow::getId).filter(Objects::nonNull).forEach(normalized::add);
    return mergeExcludedEventIds(new ArrayList<>(normalized), List.of());
  }

  public String encodeFeedCursor(List<String> excludedEventIds) {
    String payload = "v2:" + String.join(",", excludedEventIds);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  public String normalizeFeedSeed(String seed) {
    String normalized = trimToNull(seed);
    if (normalized == null) {
      return "feed-" + OffsetDateTime.now().toInstant().toEpochMilli();
    }

    return normalized.length() <= MAX_FEED_SEED_LENGTH ? normalized : normalized.substring(0, MAX_FEED_SEED_LENGTH);
  }

  private List<String> normalizeExcludedEventIds(String rawEventIds) {
    String normalizedRawEventIds = trimToNull(rawEventIds);
    if (normalizedRawEventIds == null) {
      return List.of();
    }

    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String rawEventId : normalizedRawEventIds.split(",")) {
      String eventId = trimToNull(rawEventId);
      if (eventId != null) {
        normalized.add(eventId);
      }
    }

    if (normalized.size() > MAX_FEED_EXCLUDED_EVENT_IDS) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "excludeEventIds accepts up to " + MAX_FEED_EXCLUDED_EVENT_IDS + " event ids.");
    }

    return new ArrayList<>(normalized);
  }

  private String normalizeFeedScope(String scope) {
    String normalized = trimToNull(scope);
    if (normalized == null || FEED_SCOPE_ALL.equalsIgnoreCase(normalized)) {
      return FEED_SCOPE_ALL;
    }
    if (FEED_SCOPE_FRIENDS.equalsIgnoreCase(normalized)) {
      return FEED_SCOPE_FRIENDS;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported feed scope.");
  }

  private String normalizeFeedSort(String sort) {
    String normalized = trimToNull(sort);
    if (normalized == null || FEED_SORT_DEFAULT.equalsIgnoreCase(normalized)) {
      return FEED_SORT_DEFAULT;
    }
    if (FEED_SORT_TRENDING.equalsIgnoreCase(normalized)) {
      return FEED_SORT_TRENDING;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported feed sort.");
  }

  private String normalizeAttendanceModeFilterValue(String attendanceMode) {
    String normalized = trimToNull(attendanceMode);
    if (ATTENDANCE_MODE_WAITLIST.equalsIgnoreCase(normalized)) {
      return ATTENDANCE_MODE_WAITLIST;
    }
    if (ATTENDANCE_MODE_PAID.equalsIgnoreCase(normalized)) {
      return ATTENDANCE_MODE_PAID;
    }
    if (DEFAULT_ATTENDANCE_MODE.equalsIgnoreCase(normalized)) {
      return DEFAULT_ATTENDANCE_MODE;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported attendance mode filter.");
  }

  private EventTagCatalog.TagDefinition normalizeTag(String value) {
    String normalized = trimToNull(value);
    if (normalized == null) {
      return null;
    }

    normalized = normalized.replaceFirst("^#+", "").replaceAll("\\s+", " ").trim();
    if (normalized.isEmpty()) {
      return null;
    }
    String unsupportedTag = normalized;
    return EventTagCatalog.find(normalized)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported event tag: " + unsupportedTag));
  }

  private OffsetDateTime parseOptionalWhenIso(String whenIso, String fieldName) {
    String normalized = trimToNull(whenIso);
    if (normalized == null) {
      return null;
    }
    try {
      return OffsetDateTime.parse(normalized);
    } catch (DateTimeParseException _) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + " format.");
    }
  }

  private boolean isValidCoordinate(double latitude, double longitude) {
    return Double.isFinite(latitude)
        && Double.isFinite(longitude)
        && Math.abs(latitude) <= 90
        && Math.abs(longitude) <= 180;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public record FeedFilters(
      List<String> excludeEventIds,
      OffsetDateTime from,
      OffsetDateTime to,
      Double lat,
      Double lng,
      Double radiusKm,
      String query,
      List<String> tags,
      List<String> attendanceModes,
      String scope,
      String sort) {}

  public record FeedCursor(int offset, List<String> excludeEventIds) {}
}
