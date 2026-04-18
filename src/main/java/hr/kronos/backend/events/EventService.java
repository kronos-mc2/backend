package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CoordinatesDto;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
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

  private final EventMapper eventMapper;

  public EventService(EventMapper eventMapper) {
    this.eventMapper = eventMapper;
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

  public List<AppEventDto> getFeed() {
    return eventMapper.findFeed().stream().map(this::toDto).toList();
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
    EventRow row = eventMapper.findById(eventId, userId);
    if (row == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
    }

    String currentStatus = row.getUserParticipantStatus();
    boolean shouldDecrement =
        "joined".equals(currentStatus) || "approved".equals(currentStatus) || "waitlisted".equals(currentStatus);

    eventMapper.upsertParticipant(eventId, userId, "left");
    if (shouldDecrement) {
      eventMapper.decrementParticipantCount(eventId);
    }

    EventRow updated = eventMapper.findById(eventId, userId);
    return toDto(updated);
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

  private AppEventDto toDto(EventRow row) {
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
        row.getParticipantCount(),
        isJoinedByMe(row.getUserParticipantStatus()),
        row.getUserParticipantStatus(),
        canJoin(row));
  }

  private EventRow requireJoinableEvent(String eventId, String userId) {
    EventRow row = eventMapper.findById(eventId, userId);
    if (row == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
    }

    if (!canJoin(row)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event cannot be joined.");
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
}
