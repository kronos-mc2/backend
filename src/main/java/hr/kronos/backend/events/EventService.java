package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CoordinatesDto;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.EventMediaRequest;
import hr.kronos.backend.api.dto.EventMediaDto;
import hr.kronos.backend.api.dto.EventParticipantDto;
import hr.kronos.backend.api.dto.EventRatingRequest;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import hr.kronos.backend.api.dto.UpdateEventRequest;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventMediaRow;
import hr.kronos.backend.events.persistence.EventParticipantRow;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.messages.MessageService;
import hr.kronos.backend.payments.persistence.PaymentMapper;
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
import org.springframework.scheduling.annotation.Scheduled;
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
  private final PaymentMapper paymentMapper;

  public EventService(EventMapper eventMapper, MessageService messageService, PaymentMapper paymentMapper) {
    this.eventMapper = eventMapper;
    this.messageService = messageService;
    this.paymentMapper = paymentMapper;
  }

  public List<AppEventDto> getEvents(
      String from,
      String to,
      Double lat,
      Double lng,
      Double radiusKm,
      String query,
      String userId) {
    markPastEventsFinished();
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
    markPastEventsFinished();
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
    markPastEventsFinished();
    EventRow row = getAccessibleEvent(eventId, userId);
    return toDto(row, eventMapper.findMediaByEventId(row.getId()).stream().map(this::toMediaDto).toList());
  }

  public List<AppEventDto> getMyEvents(String userId, String filter) {
    markPastEventsFinished();
    String normalizedFilter = normalizeMyEventsFilter(filter);
    return eventMapper.findByUser(userId, normalizedFilter).stream().map(this::toDto).toList();
  }

  public List<AppEventDto> getLikedEvents(String userId) {
    return toDtosWithMedia(eventMapper.findLikedByUser(userId));
  }

  public List<AppEventDto> getUpcomingCreatedByUser(String targetUserId, String userId) {
    String normalizedTargetUserId = trimToNull(targetUserId);
    if (normalizedTargetUserId == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId is required.");
    }
    return toDtosWithMedia(eventMapper.findUpcomingCreatedByUser(normalizedTargetUserId, userId));
  }

  public AppEventDto joinEvent(String eventId, String userId) {
    EventRow row = requireJoinableEvent(eventId, userId, true);
    return joinEvent(row, eventId, userId);
  }

  public AppEventDto joinEventAfterPayment(String eventId, String userId) {
    EventRow row = requireJoinableEvent(eventId, userId, false);
    return joinEvent(row, eventId, userId);
  }

  private AppEventDto joinEvent(EventRow row, String eventId, String userId) {
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

  public AppEventDto rateEvent(String eventId, EventRatingRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    EventRow row = getAccessibleEvent(eventId, userId);
    if (!canRateOrganizer(row, userId)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event cannot be rated.");
    }

    Integer eventRating = request.eventRating();
    Integer organizerRating = request.organizerRating();
    if (eventRating == null || eventRating < 1 || eventRating > 5) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventRating must be between 1 and 5.");
    }
    if (organizerRating == null || organizerRating < 1 || organizerRating > 5) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizerRating must be between 1 and 5.");
    }

    eventMapper.upsertEventRating(eventId, userId, eventRating, trimToNull(request.eventComment()));
    eventMapper.refreshEventRatingAggregate(eventId);
    eventMapper.upsertOrganizerRating(
        eventId,
        row.getCreatorUserId(),
        userId,
        organizerRating,
        trimToNull(request.organizerComment()));
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
    LocalizedInput title = localizedInput(request.title(), request.titleHr(), request.titleEn());
    LocalizedInput where = localizedInput(request.where(), request.whereHr(), request.whereEn());
    LocalizedInput about = localizedInput(request.about(), request.aboutHr(), request.aboutEn());
    LocalizedInput entryInstructions =
        optionalLocalizedInput(request.entryInstructions(), request.entryInstructionsHr(), request.entryInstructionsEn());

    EventRow row = new EventRow();
    row.setId("created-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    row.setCreatorUserId(creatorUserId);
    row.setTitleHr(title.hr());
    row.setTitleEn(title.en());
    row.setWhereHr(where.hr());
    row.setWhereEn(where.en());
    row.setAddress(firstNonBlank(request.address(), where.hr()).trim());
    row.setAboutHr(about.hr());
    row.setAboutEn(about.en());
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
    row.setEventRatingAverage(BigDecimal.ZERO);
    row.setEventRatingCount(0);
    row.setOrganizerRatingAverage(BigDecimal.ZERO);
    row.setOrganizerRatingCount(0);

    if (request.entranceCoordinates() != null) {
      row.setEntranceLatitude(request.entranceCoordinates().latitude());
      row.setEntranceLongitude(request.entranceCoordinates().longitude());
    }

    row.setEntryInstructionsHr(entryInstructions == null ? null : entryInstructions.hr());
    row.setEntryInstructionsEn(entryInstructions == null ? null : entryInstructions.en());

    eventMapper.insert(row);
    syncTicketProduct(row);
    eventMapper.upsertParticipant(row.getId(), creatorUserId, "joined");
    row.setUserParticipantStatus("joined");
    return toDto(row);
  }

  public AppEventDto updateEvent(String eventId, UpdateEventRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    EventRow current = requireOwnedEvent(eventId, userId);
    String title = firstNonBlank(request.title(), current.getTitleHr());
    String where = firstNonBlank(request.where(), current.getWhereHr());
    String about = firstNonBlank(request.about(), current.getAboutHr());
    String startAtInput = firstNonBlank(request.startAt(), firstNonBlank(request.whenISO(), timestamp(current.getStartAt())));
    OffsetDateTime startAt = parseWhenIso(startAtInput, "startAt");
    OffsetDateTime endAt = request.endAt() == null ? current.getEndAt() : parseOptionalWhenIso(request.endAt(), "endAt");
    if (endAt != null && endAt.isBefore(startAt)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt.");
    }

    String visibility = request.visibility() == null ? current.getVisibility() : normalizeVisibility(request.visibility());
    String attendanceMode =
        request.attendanceMode() == null ? current.getAttendanceMode() : normalizeAttendanceMode(request.attendanceMode());
    BigDecimal priceAmount = "paid".equals(attendanceMode) ? firstNonNull(request.priceAmount(), current.getPriceAmount()) : null;
    String rawPriceCurrency = firstNonBlank(request.priceCurrency(), current.getPriceCurrency());
    String priceCurrency = "paid".equals(attendanceMode) && rawPriceCurrency != null ? rawPriceCurrency.trim().toUpperCase() : null;
    Integer capacity = request.capacity() == null ? current.getCapacity() : request.capacity();
    validateCommercialFields(attendanceMode, priceAmount, priceCurrency, capacity);

    CoordinatesDto coordinates = request.coordinates() == null
        ? new CoordinatesDto(current.getLatitude(), current.getLongitude())
        : request.coordinates();
    validateCoordinates(coordinates, "coordinates");
    if (request.entranceCoordinates() != null) {
      validateCoordinates(request.entranceCoordinates(), "entranceCoordinates");
    }

    LocalizedInput entryInstructions = optionalLocalizedInput(
        request.entryInstructions(),
        request.entryInstructions(),
        firstNonBlank(current.getEntryInstructionsEn(), current.getEntryInstructionsHr()));

    current.setTitleHr(title.trim());
    current.setTitleEn(title.trim());
    current.setWhereHr(where.trim());
    current.setWhereEn(where.trim());
    current.setAddress(firstNonBlank(request.address(), current.getAddress()).trim());
    current.setAboutHr(about.trim());
    current.setAboutEn(about.trim());
    current.setWhenIso(startAt);
    current.setStartAt(startAt);
    current.setEndAt(endAt);
    current.setLatitude(coordinates.latitude());
    current.setLongitude(coordinates.longitude());
    current.setEntranceLatitude(request.entranceCoordinates() == null ? current.getEntranceLatitude() : request.entranceCoordinates().latitude());
    current.setEntranceLongitude(request.entranceCoordinates() == null ? current.getEntranceLongitude() : request.entranceCoordinates().longitude());
    current.setEntryInstructionsHr(entryInstructions == null ? null : entryInstructions.hr());
    current.setEntryInstructionsEn(entryInstructions == null ? null : entryInstructions.en());
    current.setVisibility(visibility);
    current.setAttendanceMode(attendanceMode);
    current.setPriceAmount(priceAmount);
    current.setPriceCurrency(priceCurrency);
    current.setCapacity(capacity);
    current.setStatus(normalizeStatus(firstNonBlank(request.status(), current.getStatus())));

    eventMapper.update(current);
    syncTicketProduct(current);
    return getEventById(eventId, userId);
  }

  public void deleteEvent(String eventId, String userId) {
    requireOwnedEvent(eventId, userId);
    eventMapper.delete(eventId);
  }

  public AppEventDto addMedia(String eventId, EventMediaRequest request, String userId) {
    requireOwnedEvent(eventId, userId);
    if (request == null || trimToNull(request.url()) == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required.");
    }

    String mediaType = normalizeMediaType(request.mediaType());
    EventMediaRow media = new EventMediaRow();
    media.setId("media-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
    media.setEventId(eventId);
    media.setMediaType(mediaType);
    media.setUrl(request.url().trim());
    media.setThumbnailUrl(trimToNull(request.thumbnailUrl()));
    media.setSortOrder(eventMapper.findMediaByEventId(eventId).size());
    eventMapper.insertMedia(media);
    return getEventById(eventId, userId);
  }

  public AppEventDto deleteMedia(String eventId, String mediaId, String userId) {
    requireOwnedEvent(eventId, userId);
    eventMapper.deleteMedia(eventId, mediaId);
    return getEventById(eventId, userId);
  }

  public List<EventParticipantDto> getParticipants(String eventId, String userId) {
    requireOwnedEvent(eventId, userId);
    return eventMapper.findParticipantsByEventId(eventId).stream().map(this::toParticipantDto).toList();
  }

  public List<EventParticipantDto> approveParticipant(String eventId, String participantUserId, String userId) {
    requireOwnedEvent(eventId, userId);
    eventMapper.updateParticipantStatus(eventId, participantUserId, "approved");
    notifyParticipant(eventId, participantUserId, "event_attendance_approved", "Dolazak odobren", "Organizator je odobrio tvoj dolazak na event.");
    return getParticipants(eventId, userId);
  }

  public List<EventParticipantDto> removeParticipant(String eventId, String participantUserId, String userId) {
    EventRow event = requireOwnedEvent(eventId, userId);
    if (participantUserId.equals(userId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner cannot be removed from the event.");
    }
    boolean active = eventMapper.findParticipantsByEventId(eventId).stream()
        .anyMatch((participant) ->
            participantUserId.equals(participant.getUserId())
                && ("joined".equals(participant.getStatus())
                    || "approved".equals(participant.getStatus())
                    || "waitlisted".equals(participant.getStatus())));
    eventMapper.updateParticipantStatus(eventId, participantUserId, "rejected");
    if (active) {
      eventMapper.decrementParticipantCount(event.getId());
    }
    messageService.leaveEventChatRoom(eventId, participantUserId);
    notifyParticipant(eventId, participantUserId, "event_attendance_removed", "Maknut si s eventa", "Organizator te maknuo s eventa.");
    return getParticipants(eventId, userId);
  }

  public List<EventParticipantDto> blockParticipant(String eventId, String participantUserId, String userId) {
    EventRow event = requireOwnedEvent(eventId, userId);
    if ("paid".equals(event.getAttendanceMode())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid events do not support event-level blocking.");
    }
    if (participantUserId.equals(userId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner cannot be blocked from the event.");
    }

    boolean active = eventMapper.findParticipantsByEventId(eventId).stream()
        .anyMatch((participant) ->
            participantUserId.equals(participant.getUserId())
                && ("joined".equals(participant.getStatus())
                    || "approved".equals(participant.getStatus())
                    || "waitlisted".equals(participant.getStatus())));
    eventMapper.blockUser(eventId, participantUserId, userId);
    eventMapper.updateParticipantStatus(eventId, participantUserId, "rejected");
    if (active) {
      eventMapper.decrementParticipantCount(event.getId());
    }
    messageService.leaveEventChatRoom(eventId, participantUserId);
    notifyParticipant(eventId, participantUserId, "event_attendance_blocked", "Blokiran si za event", "Organizator te blokirao za ovaj event.");
    return getParticipants(eventId, userId);
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
        row.getEventRatingAverage() == null ? BigDecimal.ZERO : row.getEventRatingAverage(),
        row.getEventRatingCount(),
        row.getOrganizerRatingAverage() == null ? BigDecimal.ZERO : row.getOrganizerRatingAverage(),
        row.getOrganizerRatingCount(),
        row.getLikeCount(),
        Boolean.TRUE.equals(row.getLikedByMe()),
        row.getParticipantCount(),
        row.getWaitlistCount(),
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

  private EventRow requireJoinableEvent(String eventId, String userId, boolean requirePaidReceipt) {
    EventRow row = getAccessibleEvent(eventId, userId);
    if (!canJoin(row)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event cannot be joined.");
    }
    if (requirePaidReceipt
        && "paid".equals(row.getAttendanceMode())
        && !isJoinedByMe(row.getUserParticipantStatus())
        && !paymentMapper.hasSucceededTicketOrder(eventId, userId)) {
      throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Paid event requires ticket checkout.");
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

  private EventRow requireOwnedEvent(String eventId, String userId) {
    EventRow row = eventMapper.findById(eventId, userId);
    if (row == null || row.getCreatorUserId() == null || !row.getCreatorUserId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found.");
    }
    return row;
  }

  private EventParticipantDto toParticipantDto(EventParticipantRow row) {
    return new EventParticipantDto(
        row.getUserId(),
        row.getFullName(),
        row.getAvatarUrl(),
        row.getStatus(),
        timestamp(row.getJoinedAt()),
        timestamp(row.getApprovedAt()),
        Boolean.TRUE.equals(row.getBlocked()));
  }

  private boolean canJoin(EventRow row) {
    if (!DEFAULT_STATUS.equals(row.getStatus())) {
      return false;
    }

    Integer capacity = row.getCapacity();
    if (isJoinedByMe(row.getUserParticipantStatus())) {
      return true;
    }

    if ("blocked".equals(row.getUserParticipantStatus()) || "rejected".equals(row.getUserParticipantStatus())) {
      return false;
    }

    if ("waitlist".equals(row.getAttendanceMode())) {
      return true;
    }

    return capacity == null || row.getParticipantCount() < capacity;
  }

  private boolean isJoinedByMe(String status) {
    return "joined".equals(status) || "approved".equals(status) || "waitlisted".equals(status);
  }

  @Scheduled(fixedDelayString = "PT1H")
  public void markPastEventsFinished() {
    eventMapper.markPastEventsFinished();
  }

  private void notifyParticipant(String eventId, String userId, String type, String title, String body) {
    eventMapper.insertNotification(
        "notif-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18),
        userId,
        type,
        title,
        body,
        eventId);
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

    requireLocalizedInput(request.title(), request.titleHr(), request.titleEn(), "title");
    requireLocalizedInput(request.where(), request.whereHr(), request.whereEn(), "where");
    requireLocalizedInput(request.about(), request.aboutHr(), request.aboutEn(), "about");
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

  private void requireLocalizedInput(String canonical, String hr, String en, String fieldName) {
    if (trimToNull(canonical) != null) {
      return;
    }

    if (trimToNull(hr) == null || trimToNull(en) == null) {
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

  private String normalizeStatus(String status) {
    String normalized = trimToNull(status);
    if (normalized == null) {
      return DEFAULT_STATUS;
    }

    if ("draft".equalsIgnoreCase(normalized)
        || "published".equalsIgnoreCase(normalized)
        || "cancelled".equalsIgnoreCase(normalized)
        || "finished".equalsIgnoreCase(normalized)) {
      return normalized.toLowerCase();
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported event status.");
  }

  private String normalizeMediaType(String mediaType) {
    String normalized = trimToNull(mediaType);
    if (normalized == null || "image".equalsIgnoreCase(normalized)) {
      return "image";
    }
    if ("video".equalsIgnoreCase(normalized)) {
      return "video";
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported media type.");
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

  private void syncTicketProduct(EventRow row) {
    if (!"paid".equals(row.getAttendanceMode())) {
      return;
    }

    paymentMapper.upsertTicketProduct(
        ticketProductId(row.getId()),
        row.getId(),
        trimProductName(row.getTitleHr()),
        row.getPriceAmount(),
        row.getPriceCurrency());
  }

  private String ticketProductId(String eventId) {
    return "ticket-" + eventId.substring(0, Math.min(eventId.length(), 57));
  }

  private String trimProductName(String value) {
    if (value == null || value.isBlank()) {
      return "Event ticket";
    }

    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.length() > 180 ? normalized.substring(0, 180) : normalized;
  }

  private String firstNonBlank(String primary, String fallback) {
    String normalizedPrimary = trimToNull(primary);
    return normalizedPrimary != null ? normalizedPrimary : fallback;
  }

  private <T> T firstNonNull(T primary, T fallback) {
    return primary != null ? primary : fallback;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }

    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private LocalizedInput localizedInput(String canonical, String hr, String en) {
    String normalized = trimToNull(canonical);
    if (normalized != null) {
      return new LocalizedInput(normalized, normalized);
    }

    return new LocalizedInput(trimToNull(hr), trimToNull(en));
  }

  private LocalizedInput optionalLocalizedInput(String canonical, String hr, String en) {
    String normalized = trimToNull(canonical);
    if (normalized != null) {
      return new LocalizedInput(normalized, normalized);
    }

    String normalizedHr = trimToNull(hr);
    String normalizedEn = trimToNull(en);
    if (normalizedHr == null && normalizedEn == null) {
      return null;
    }

    String fallback = normalizedHr == null ? normalizedEn : normalizedHr;
    return new LocalizedInput(
        normalizedHr == null ? fallback : normalizedHr,
        normalizedEn == null ? fallback : normalizedEn);
  }

  private String timestamp(OffsetDateTime value) {
    return value == null ? null : value.toInstant().toString();
  }

  private record FeedCursor(OffsetDateTime startAt, String eventId) {}

  private record LocalizedInput(String hr, String en) {}
}
