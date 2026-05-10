package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CoordinatesDto;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.EventMediaDto;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventMediaRow;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.messages.MessageService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {
  private static final String CREATED_EVENT_TYPE = "created";
  private static final String DEFAULT_STATUS = "published";
  private static final String DEFAULT_ATTENDANCE_MODE = "open";
  private static final String DEFAULT_VISIBILITY = "public";
  private static final String MY_EVENTS_FILTER_ALL = "all";
  private static final String MY_EVENTS_FILTER_JOINED = "joined";
  private static final String MY_EVENTS_FILTER_CREATED = "created";
  private static final int DEFAULT_FEED_LIMIT = 5;
  private static final int MAX_FEED_LIMIT = 10;

  private final EventMapper eventMapper;
  private final MessageService messageService;

  public EventService(EventMapper eventMapper, MessageService messageService) {
    this.eventMapper = eventMapper;
    this.messageService = messageService;
  }

  public List<AppEventDto> getEvents(
      String from,
      String to,
      Double lat,
      Double lng,
      Double radiusKm,
      String query,
      String userId) {
    OffsetDateTime fromDate = parseOptionalWhenIso(from, "from");
    OffsetDateTime toDate = parseOptionalWhenIso(to, "to");

    if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
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

    String normalizedQuery = trimToNull(query);
    return eventMapper.findAll(fromDate, toDate, lat, lng, radiusKm, normalizedQuery, userId).stream().map(this::toDto).toList();
  }

  public FeedPageDto getFeed(String userId, String cursor, Integer limit) {
    FeedCursor parsedCursor = parseFeedCursor(cursor);
    int normalizedLimit = normalizeFeedLimit(limit);
    List<EventRow> rows =
        eventMapper.findFeedPage(
            userId,
            parsedCursor == null ? null : parsedCursor.startAt(),
            parsedCursor == null ? null : parsedCursor.eventId(),
            normalizedLimit + 1);

    boolean hasMore = rows.size() > normalizedLimit;
    List<EventRow> pageRows = hasMore ? rows.subList(0, normalizedLimit) : rows;
    String nextCursor = hasMore ? encodeFeedCursor(pageRows.get(pageRows.size() - 1)) : null;
    return new FeedPageDto(toDtosWithMedia(pageRows), nextCursor, hasMore);
  }

  public AppEventDto getEventById(String eventId, String userId) {
    EventRow row = getAccessibleEvent(eventId, userId);
    return toDto(row, eventMapper.findMediaByEventId(row.getId()).stream().map(this::toMediaDto).toList());
  }

  public List<AppEventDto> getMyEvents(String userId, String filter) {
    String normalizedFilter = normalizeMyEventsFilter(filter);
    return eventMapper.findByUser(userId, normalizedFilter).stream().map(this::toDto).toList();
  }

  public List<AppEventDto> getLikedEvents(String userId) {
    return toDtosWithMedia(eventMapper.findLikedByUser(userId));
  }

  public AppEventDto joinEvent(String eventId, String userId) {
    EventRow row = requireJoinableEvent(eventId, userId);
    String currentStatus = row.getUserParticipantStatus();
    boolean shouldIncrement =
        currentStatus == null || "left".equals(currentStatus) || "rejected".equals(currentStatus);
    String nextStatus = "waitlist".equals(row.getAttendanceMode()) ? "waitlisted" : "joined";

    eventMapper.upsertParticipant(eventId, userId, nextStatus);
    if (shouldIncrement) {
      eventMapper.incrementParticipantCount(eventId);
    }

    EventRow updated = eventMapper.findById(eventId, userId);
    return toDto(updated);
  }

  public AppEventDto leaveEvent(String eventId, String userId) {
    EventRow row = getAccessibleEvent(eventId, userId);
    String currentStatus = row.getUserParticipantStatus();
    boolean shouldDecrement =
        "joined".equals(currentStatus) || "approved".equals(currentStatus) || "waitlisted".equals(currentStatus);

    eventMapper.upsertParticipant(eventId, userId, "left");
    if (shouldDecrement) {
      eventMapper.decrementParticipantCount(eventId);
    }
    messageService.leaveEventChatRoom(eventId, userId);

    EventRow updated = eventMapper.findById(eventId, userId);
    return toDto(updated);
  }

  public AppEventDto rateOrganizer(String eventId, OrganizerRatingRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }
    if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating must be between 1 and 5.");
    }

    EventRow row = getAccessibleEvent(eventId, userId);
    if (!canRateOrganizer(row, userId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Organizer cannot be rated for this event.");
    }

    eventMapper.upsertOrganizerRating(
        eventId,
        row.getCreatorUserId(),
        userId,
        request.rating(),
        trimToNull(request.comment()));
    eventMapper.refreshOrganizerRatingAggregate(eventId);
    return getEventById(eventId, userId);
  }

  public AppEventDto likeEvent(String eventId, String userId) {
    getAccessibleEvent(eventId, userId);
    eventMapper.insertLike(eventId, userId);
    return getEventById(eventId, userId);
  }

  public AppEventDto unlikeEvent(String eventId, String userId) {
    getAccessibleEvent(eventId, userId);
    eventMapper.deleteLike(eventId, userId);
    return getEventById(eventId, userId);
  }

  public AppEventDto createEvent(CreateEventRequest request, String creatorUserId) {
    validateRequest(request);

    OffsetDateTime startAt = parseWhenIso(firstNonBlank(request.startAt(), request.whenISO()), "startAt");
    OffsetDateTime endAt = parseOptionalWhenIso(request.endAt(), "endAt");
    if (endAt != null && endAt.isBefore(startAt)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt.");
    }

    String visibility = normalizeVisibility(request.visibility());
    String attendanceMode = normalizeAttendanceMode(request.attendanceMode());
    validateCommercialFields(attendanceMode, request.priceAmount(), request.priceCurrency(), request.capacity());

    EventRow row = new EventRow();
    row.setId("created-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    row.setCreatorUserId(creatorUserId);
    row.setTitleHr(request.titleHr().trim());
    row.setTitleEn(request.titleEn().trim());
    row.setWhereHr(request.whereHr().trim());
    row.setWhereEn(request.whereEn().trim());
    row.setAddress(firstNonBlank(request.address(), request.whereHr()).trim());
    row.setAboutHr(request.aboutHr().trim());
    row.setAboutEn(request.aboutEn().trim());
    row.setWhenIso(startAt);
    row.setStartAt(startAt);
    row.setEndAt(endAt);
    row.setEventType(CREATED_EVENT_TYPE);
    row.setLatitude(request.coordinates().latitude());
    row.setLongitude(request.coordinates().longitude());
    row.setParticipantCount(1);
    row.setVisibility(visibility);
    row.setAttendanceMode(attendanceMode);
    row.setPriceAmount("paid".equals(attendanceMode) ? request.priceAmount() : null);
    row.setPriceCurrency("paid".equals(attendanceMode) ? request.priceCurrency().trim().toUpperCase() : null);
    row.setCapacity(request.capacity());
    row.setStatus(DEFAULT_STATUS);
    row.setOrganizerRatingAverage(BigDecimal.ZERO);
    row.setOrganizerRatingCount(0);

    if (request.entranceCoordinates() != null) {
      row.setEntranceLatitude(request.entranceCoordinates().latitude());
      row.setEntranceLongitude(request.entranceCoordinates().longitude());
    }

    row.setEntryInstructionsHr(trimToNull(request.entryInstructionsHr()));
    row.setEntryInstructionsEn(trimToNull(request.entryInstructionsEn()));

    eventMapper.insert(row);
    eventMapper.upsertParticipant(row.getId(), creatorUserId, "joined");
    row.setUserParticipantStatus("joined");
    return toDto(row);
  }

  public AppEventDto toDto(EventRow row) {
    return toDto(row, null);
  }

  private AppEventDto toDto(EventRow row, List<EventMediaDto> media) {
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
        row.getOrganizerRatingAverage() == null ? BigDecimal.ZERO : row.getOrganizerRatingAverage(),
        row.getOrganizerRatingCount(),
        row.getLikeCount(),
        Boolean.TRUE.equals(row.getLikedByMe()),
        row.getParticipantCount(),
        isJoinedByMe(row.getUserParticipantStatus()),
        row.getUserParticipantStatus(),
        canJoin(row),
        media == null || media.isEmpty() ? null : media);
  }

  private List<AppEventDto> toDtosWithMedia(List<EventRow> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }

    Map<String, List<EventMediaDto>> mediaByEventId = loadMediaByEventIds(rows);
    return rows.stream().map((row) -> toDto(row, mediaByEventId.get(row.getId()))).toList();
  }

  private EventMediaDto toMediaDto(EventMediaRow row) {
    return new EventMediaDto(row.getId(), row.getMediaType(), row.getUrl(), row.getThumbnailUrl(), row.getSortOrder());
  }

  private Map<String, List<EventMediaDto>> loadMediaByEventIds(List<EventRow> rows) {
    List<String> eventIds = rows.stream().map(EventRow::getId).distinct().toList();
    Map<String, List<EventMediaDto>> mediaByEventId = new HashMap<>();

    for (EventMediaRow mediaRow : eventMapper.findMediaByEventIds(eventIds)) {
      mediaByEventId.computeIfAbsent(mediaRow.getEventId(), (ignored) -> new java.util.ArrayList<>()).add(toMediaDto(mediaRow));
    }

    return mediaByEventId;
  }

  private EventRow requireJoinableEvent(String eventId, String userId) {
    EventRow row = getAccessibleEvent(eventId, userId);
    if (!canJoin(row)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event cannot be joined.");
    }

    return row;
  }

  private EventRow getAccessibleEvent(String eventId, String userId) {
    EventRow row = eventMapper.findAccessibleById(eventId, userId);
    if (row == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
    }
    return row;
  }

  private boolean canJoin(EventRow row) {
    if (!DEFAULT_STATUS.equals(row.getStatus())) {
      return false;
    }

    Integer capacity = row.getCapacity();
    if (isJoinedByMe(row.getUserParticipantStatus())) {
      return true;
    }

    if ("waitlist".equals(row.getAttendanceMode())) {
      return true;
    }

    return capacity == null || row.getParticipantCount() < capacity;
  }

  private boolean isJoinedByMe(String status) {
    return "joined".equals(status) || "approved".equals(status) || "waitlisted".equals(status);
  }

  private boolean canRateOrganizer(EventRow row, String userId) {
    if (row.getCreatorUserId() == null || row.getCreatorUserId().equals(userId)) {
      return false;
    }
    if (!"joined".equals(row.getUserParticipantStatus()) && !"approved".equals(row.getUserParticipantStatus())) {
      return false;
    }
    if ("finished".equals(row.getStatus())) {
      return true;
    }

    OffsetDateTime endAt = row.getEndAt() == null ? row.getStartAt() : row.getEndAt();
    if (endAt == null) {
      endAt = row.getWhenIso();
    }
    return endAt != null && endAt.isBefore(OffsetDateTime.now());
  }

  private int normalizeFeedLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_FEED_LIMIT;
    }

    if (limit < 1 || limit > MAX_FEED_LIMIT) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_FEED_LIMIT + ".");
    }

    return limit;
  }

  private FeedCursor parseFeedCursor(String cursor) {
    String normalized = trimToNull(cursor);
    if (normalized == null) {
      return null;
    }

    try {
      String decoded = new String(Base64.getUrlDecoder().decode(normalized), StandardCharsets.UTF_8);
      int separatorIndex = decoded.indexOf('|');
      if (separatorIndex <= 0 || separatorIndex >= decoded.length() - 1) {
        throw new IllegalArgumentException("Invalid feed cursor.");
      }

      OffsetDateTime startAt = parseWhenIso(decoded.substring(0, separatorIndex), "cursor");
      String eventId = decoded.substring(separatorIndex + 1);
      return new FeedCursor(startAt, eventId);
    } catch (IllegalArgumentException | ResponseStatusException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor format.");
    }
  }

  private String encodeFeedCursor(EventRow row) {
    String rawCursor = timestamp(row.getStartAt() == null ? row.getWhenIso() : row.getStartAt()) + "|" + row.getId();
    return Base64.getUrlEncoder().withoutPadding().encodeToString(rawCursor.getBytes(StandardCharsets.UTF_8));
  }

  private String normalizeMyEventsFilter(String filter) {
    String normalized = trimToNull(filter);
    if (normalized == null || MY_EVENTS_FILTER_ALL.equalsIgnoreCase(normalized)) {
      return MY_EVENTS_FILTER_ALL;
    }

    if (MY_EVENTS_FILTER_JOINED.equalsIgnoreCase(normalized)) {
      return MY_EVENTS_FILTER_JOINED;
    }

    if (MY_EVENTS_FILTER_CREATED.equalsIgnoreCase(normalized)) {
      return MY_EVENTS_FILTER_CREATED;
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported my events filter.");
  }

  private void validateRequest(CreateEventRequest request) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    requireNonBlank(request.titleHr(), "titleHr");
    requireNonBlank(request.titleEn(), "titleEn");
    requireNonBlank(request.whereHr(), "whereHr");
    requireNonBlank(request.whereEn(), "whereEn");
    requireNonBlank(request.aboutHr(), "aboutHr");
    requireNonBlank(request.aboutEn(), "aboutEn");
    requireNonBlank(firstNonBlank(request.startAt(), request.whenISO()), "startAt");

    if (request.coordinates() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coordinates are required.");
    }

    validateCoordinates(request.coordinates(), "coordinates");
    if (request.entranceCoordinates() != null) {
      validateCoordinates(request.entranceCoordinates(), "entranceCoordinates");
    }
  }

  private void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required.");
    }
  }

  private void validateCoordinates(CoordinatesDto coordinates, String fieldName) {
    if (!isValidCoordinate(coordinates.latitude(), coordinates.longitude())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " are invalid.");
    }
  }

  private boolean isValidCoordinate(double latitude, double longitude) {
    return Double.isFinite(latitude)
        && Double.isFinite(longitude)
        && Math.abs(latitude) <= 90
        && Math.abs(longitude) <= 180;
  }

  private OffsetDateTime parseWhenIso(String whenIso, String fieldName) {
    try {
      return OffsetDateTime.parse(whenIso);
    } catch (DateTimeParseException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + " format.");
    }
  }

  private OffsetDateTime parseOptionalWhenIso(String whenIso, String fieldName) {
    String normalized = trimToNull(whenIso);
    return normalized == null ? null : parseWhenIso(normalized, fieldName);
  }

  private String normalizeVisibility(String visibility) {
    if ("friends".equalsIgnoreCase(visibility) || "private".equalsIgnoreCase(visibility)) {
      return "friends";
    }

    return DEFAULT_VISIBILITY;
  }

  private String normalizeAttendanceMode(String attendanceMode) {
    if ("waitlist".equalsIgnoreCase(attendanceMode)) {
      return "waitlist";
    }

    if ("paid".equalsIgnoreCase(attendanceMode)) {
      return "paid";
    }

    return DEFAULT_ATTENDANCE_MODE;
  }

  private void validateCommercialFields(String attendanceMode, BigDecimal priceAmount, String priceCurrency, Integer capacity) {
    if (capacity != null && capacity <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity must be greater than 0.");
    }

    if (!"paid".equals(attendanceMode)) {
      return;
    }

    if (priceAmount == null || priceAmount.compareTo(BigDecimal.ZERO) < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "priceAmount is required for paid events.");
    }

    String normalizedCurrency = trimToNull(priceCurrency);
    if (normalizedCurrency == null || normalizedCurrency.length() != 3) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "priceCurrency must be a 3-letter code.");
    }
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

  private String timestamp(OffsetDateTime value) {
    return value == null ? null : value.toInstant().toString();
  }

  private record FeedCursor(OffsetDateTime startAt, String eventId) {}
}
