package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CoordinatesDto;
import hr.kronos.backend.api.dto.EventCacheStateDto;
import hr.kronos.backend.api.dto.CreateFeedPreferenceRequest;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.EventMediaRequest;
import hr.kronos.backend.api.dto.EventMediaDto;
import hr.kronos.backend.api.dto.EventParticipantDto;
import hr.kronos.backend.api.dto.EventRatingRequest;
import hr.kronos.backend.api.dto.FeedImpressionRequest;
import hr.kronos.backend.api.dto.FeedPreferenceDto;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import hr.kronos.backend.api.dto.SavedEventsOverviewDto;
import hr.kronos.backend.api.dto.UpdateEventRequest;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventMediaRow;
import hr.kronos.backend.events.persistence.EventNotificationRecipientRow;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.messages.MessageService;
import hr.kronos.backend.notifications.NotificationService;
import hr.kronos.backend.storage.StoredObjectContent;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@SuppressWarnings("java:S107")
public class EventDomainService {
  private static final String CREATED_EVENT_TYPE = "created";
  private static final String STATUS_PUBLISHED = "published";
  private static final String DEFAULT_STATUS = STATUS_PUBLISHED;
  private static final String DEFAULT_ATTENDANCE_MODE = "open";
  private static final String DEFAULT_VISIBILITY = "public";
  private static final String VISIBILITY_FRIENDS = "friends";
  private static final String FEED_PREFERENCE_TYPE_EVENT = "event";
  private static final String FIELD_START_AT = "startAt";
  private static final String ATTENDANCE_MODE_PAID = "paid";
  private static final String ATTENDANCE_MODE_WAITLIST = "waitlist";
  private static final String MEDIA_TYPE_IMAGE = "image";
  private static final String MEDIA_TYPE_VIDEO = "video";
  private static final String MEDIA_ID_PREFIX = "media-";
  private static final String PARTICIPANT_STATUS_APPROVED = "approved";
  private static final String PARTICIPANT_STATUS_JOINED = "joined";
  private static final String PARTICIPANT_STATUS_LEFT = "left";
  private static final String PARTICIPANT_STATUS_REJECTED = "rejected";
  private static final String PARTICIPANT_STATUS_WAITLISTED = "waitlisted";
  private static final String MY_EVENTS_FILTER_ALL = "all";
  private static final String MY_EVENTS_FILTER_JOINED = PARTICIPANT_STATUS_JOINED;
  private static final String MY_EVENTS_FILTER_CREATED = "created";
  private static final String STATUS_CANCELLED = "cancelled";
  private static final String STATUS_DRAFT = "draft";
  private static final String STATUS_FINISHED = "finished";
  private static final int SAVED_COLLECTION_PREVIEW_LIMIT = 12;
  private static final int SAVED_GOING_SOON_LIMIT = 3;
  private static final int SAVED_PAST_LIMIT = 3;
  private static final int MAX_EVENT_TAGS = 10;
  private static final String NOTIFICATION_EVENT_UPDATED = "event_updated";
  private static final String NOTIFICATION_WAITLIST_APPROVED = "event_attendance_approved";
  private static final String NOTIFICATION_REMINDER_WEEK = "event_reminder_week";
  private static final String NOTIFICATION_REMINDER_DAY = "event_reminder_day";
  private static final String EVENT_TITLE_FALLBACK = "Event";

  private final EventMapper eventMapper;
  private final MessageService messageService;
  private final NotificationService notificationService;
  private final EventTicketService eventTicketService;
  private final EventMediaStorageService mediaStorageService;
  private final EventDtoMapper eventDtoMapper;
  private final EventFeedRequestNormalizer feedRequestNormalizer;

  public EventDomainService(
      EventMapper eventMapper,
      MessageService messageService,
      NotificationService notificationService,
      EventTicketService eventTicketService,
      EventMediaStorageService mediaStorageService,
      EventDtoMapper eventDtoMapper,
      EventFeedRequestNormalizer feedRequestNormalizer) {
    this.eventMapper = eventMapper;
    this.messageService = messageService;
    this.notificationService = notificationService;
    this.eventTicketService = eventTicketService;
    this.mediaStorageService = mediaStorageService;
    this.eventDtoMapper = eventDtoMapper;
    this.feedRequestNormalizer = feedRequestNormalizer;
  }

  public List<AppEventDto> getEvents(
      String from,
      String to,
      Double lat,
      Double lng,
      Double radiusKm,
      String query,
      String tags,
      String attendanceModes,
      String userId) {
    markPastEventsFinished();
    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime fromDate = parseOptionalWhenIso(from, "from");
    OffsetDateTime toDate = parseOptionalWhenIso(to, "to");
    if (fromDate == null || fromDate.isBefore(now)) {
      fromDate = now;
    }

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
    List<String> normalizedTags = feedRequestNormalizer.normalizeFilterTags(tags);
    List<String> normalizedAttendanceModes = feedRequestNormalizer.normalizeAttendanceModeFilter(attendanceModes);
    return toDtosWithMedia(
        eventMapper.findAll(
            fromDate,
            toDate,
            lat,
            lng,
            radiusKm,
            normalizedQuery,
            normalizedTags,
            normalizedAttendanceModes,
            userId));
  }

  public FeedPageDto getFeed(
      String userId,
      String cursor,
      Integer limit,
      String seed,
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
    markPastEventsFinished();
    EventFeedRequestNormalizer.FeedCursor feedCursor = feedRequestNormalizer.parseFeedCursor(cursor);
    int normalizedLimit = feedRequestNormalizer.normalizeFeedLimit(limit);
    String normalizedSeed = feedRequestNormalizer.normalizeFeedSeed(seed);
    EventFeedRequestNormalizer.FeedFilters filters =
        feedRequestNormalizer.normalizeFeedFilters(excludeEventIds, from, to, lat, lng, radiusKm, query, tags, attendanceModes, scope, sort);
    List<String> feedExcludedEventIds = feedRequestNormalizer.mergeExcludedEventIds(filters.excludeEventIds(), feedCursor.excludeEventIds());
    List<EventRow> rows =
        eventMapper.findFeedPage(
            userId,
            normalizedSeed,
            feedExcludedEventIds,
            filters.from(),
            filters.to(),
            filters.lat(),
            filters.lng(),
            filters.radiusKm(),
            filters.query(),
            filters.tags(),
            filters.attendanceModes(),
            filters.scope(),
            filters.sort(),
            feedCursor.offset(),
            normalizedLimit + 1);

    boolean hasMore = rows.size() > normalizedLimit;
    List<EventRow> pageRows = hasMore ? rows.subList(0, normalizedLimit) : rows;
    String nextCursor = hasMore ? feedRequestNormalizer.encodeFeedCursor(feedRequestNormalizer.appendReturnedEventIds(feedExcludedEventIds, pageRows)) : null;
    return new FeedPageDto(toDtosWithMedia(pageRows), nextCursor, hasMore);
  }


  public void recordFeedImpression(FeedImpressionRequest request, String userId) {
    if (request == null || trimToNull(request.eventId()) == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventId is required.");
    }

    getAccessibleEvent(request.eventId(), userId);
    eventMapper.recordFeedImpression(request.eventId(), userId);
  }

  public AppEventDto getEventById(String eventId, String userId) {
    markPastEventsFinished();
    EventRow row = getAccessibleEvent(eventId, userId);
    return eventDtoMapper.toDto(row, eventMapper.findMediaByEventId(row.getId()).stream().map(eventDtoMapper::toMediaDto).toList());
  }

  public EventCacheStateDto getEventCacheState(String eventId, String userId) {
    markPastEventsFinished();
    EventRow row = getAccessibleEvent(eventId, userId);
    return new EventCacheStateDto(row.getId(), eventDtoMapper.eventCacheVersion(row), eventDtoMapper.timestamp(row.getUpdatedAt()));
  }

  public List<AppEventDto> getMyEvents(String userId, String filter) {
    markPastEventsFinished();
    String normalizedFilter = normalizeMyEventsFilter(filter);
    return toDtosWithMedia(eventMapper.findByUser(userId, normalizedFilter));
  }

  public List<AppEventDto> getLikedEvents(String userId) {
    return toDtosWithMedia(eventMapper.findLikedByUser(userId));
  }

  public SavedEventsOverviewDto getSavedEventsOverview(String userId) {
    markPastEventsFinished();
    List<AppEventDto> likedEvents =
        toDtosWithMedia(eventMapper.findLikedByUser(userId)).stream()
            .limit(SAVED_COLLECTION_PREVIEW_LIMIT)
            .toList();

    OffsetDateTime now = OffsetDateTime.now();
    List<AppEventDto> joinedEvents = toDtosWithMedia(eventMapper.findByUser(userId, MY_EVENTS_FILTER_JOINED));
    List<AppEventDto> goingSoon =
        joinedEvents.stream()
            .filter(this::isActiveAttendance)
            .filter(event -> !eventStart(event).isBefore(now))
            .sorted((left, right) -> eventStart(left).compareTo(eventStart(right)))
            .limit(SAVED_GOING_SOON_LIMIT)
            .toList();
    List<AppEventDto> pastEvents =
        joinedEvents.stream()
            .filter(this::isActiveAttendance)
            .filter(event -> eventStart(event).isBefore(now))
            .sorted((left, right) -> eventStart(right).compareTo(eventStart(left)))
            .limit(SAVED_PAST_LIMIT)
            .toList();

    return new SavedEventsOverviewDto(likedEvents, goingSoon, pastEvents);
  }

  public List<FeedPreferenceDto> getFeedPreferences(String userId) {
    return eventMapper.findFeedPreferences(userId).stream().map(eventDtoMapper::toFeedPreferenceDto).toList();
  }


  public FeedPreferenceDto createFeedPreference(CreateFeedPreferenceRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    String type = normalizeFeedPreferenceType(request.type());
    String targetId = normalizeFeedPreferenceTarget(type, request.targetId());
    if (FEED_PREFERENCE_TYPE_EVENT.equals(type)) {
      getAccessibleEvent(targetId, userId);
    }
    String label = firstNonBlank(request.label(), targetId);
    String id = "feed-block-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    eventMapper.insertFeedPreference(id, userId, type, targetId, trimLabel(label, 180));
    if (FEED_PREFERENCE_TYPE_EVENT.equals(type)) {
      eventMapper.markFeedInteraction(targetId, userId);
    }
    return getFeedPreferences(userId).stream()
        .filter(preference -> preference.type().equals(type) && preference.targetId().equals(targetId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Preference not found."));
  }


  public void deleteFeedPreference(String preferenceId, String userId) {
    if (trimToNull(preferenceId) == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "preferenceId is required.");
    }
    eventMapper.deleteFeedPreference(preferenceId, userId);
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
        currentStatus == null || PARTICIPANT_STATUS_LEFT.equals(currentStatus) || PARTICIPANT_STATUS_REJECTED.equals(currentStatus);
    String nextStatus = ATTENDANCE_MODE_WAITLIST.equals(row.getAttendanceMode()) ? PARTICIPANT_STATUS_WAITLISTED : PARTICIPANT_STATUS_JOINED;

    eventMapper.upsertParticipant(eventId, userId, nextStatus);
    eventMapper.markFeedInteraction(eventId, userId);
    if (shouldIncrement) {
      eventMapper.incrementParticipantCount(eventId);
    }

    return getEventById(eventId, userId);
  }


  public AppEventDto leaveEvent(String eventId, String userId) {
    EventRow row = getAccessibleEvent(eventId, userId);
    String currentStatus = row.getUserParticipantStatus();
    boolean shouldDecrement =
        PARTICIPANT_STATUS_JOINED.equals(currentStatus) || PARTICIPANT_STATUS_APPROVED.equals(currentStatus) || PARTICIPANT_STATUS_WAITLISTED.equals(currentStatus);

    eventMapper.upsertParticipant(eventId, userId, PARTICIPANT_STATUS_LEFT);
    if (shouldDecrement) {
      eventMapper.decrementParticipantCount(eventId);
    }
    messageService.leaveEventChatRoom(eventId, userId);

    return getEventById(eventId, userId);
  }


  public AppEventDto rateOrganizer(String eventId, OrganizerRatingRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }
    if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "rating must be between 1 and 5.");
    }

    EventRow row = getAccessibleEvent(eventId, userId);
    if (!EventRules.canRateOrganizer(row, userId)) {
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
    if (!EventRules.canRateOrganizer(row, userId)) {
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
    eventMapper.markFeedInteraction(eventId, userId);
    return getEventById(eventId, userId);
  }


  public AppEventDto unlikeEvent(String eventId, String userId) {
    getAccessibleEvent(eventId, userId);
    eventMapper.deleteLike(eventId, userId);
    return getEventById(eventId, userId);
  }


  public AppEventDto createEvent(CreateEventRequest request, String creatorUserId) {
    validateRequest(request);

    OffsetDateTime startAt = parseWhenIso(firstNonBlank(request.startAt(), request.whenISO()), FIELD_START_AT);
    OffsetDateTime endAt = parseOptionalWhenIso(request.endAt(), "endAt");
    if (endAt != null && endAt.isBefore(startAt)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt.");
    }

    String visibility = normalizeVisibility(request.visibility());
    String attendanceMode = normalizeAttendanceMode(request.attendanceMode());
    validateCommercialFields(attendanceMode, request.priceAmount(), request.priceCurrency(), request.capacity());
    if (request.media() != null && !request.media().isEmpty()) {
      ensurePublicVisibilityForExternalMedia(visibility);
    }
    List<String> tags = normalizeTags(request.tags());
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
    row.setPriceAmount(ATTENDANCE_MODE_PAID.equals(attendanceMode) ? request.priceAmount() : null);
    row.setPriceCurrency(ATTENDANCE_MODE_PAID.equals(attendanceMode) ? request.priceCurrency().trim().toUpperCase() : null);
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
    syncTags(row.getId(), tags);
    syncTicketProduct(row);
    eventMapper.upsertParticipant(row.getId(), creatorUserId, PARTICIPANT_STATUS_JOINED);
    row.setUserParticipantStatus(PARTICIPANT_STATUS_JOINED);
    row.setTagsCsv(String.join(",", tags));
    insertUrlMedia(row.getId(), request.media(), visibility);
    return getEventById(row.getId(), creatorUserId);
  }


  public AppEventDto createEventWithImages(
      CreateEventRequest request,
      List<MultipartFile> images,
      MultipartFile video,
      String creatorUserId) {
    mediaStorageService.validateUploadedImageCount(images, true);
    mediaStorageService.validateUploadedVideoCount(video == null || video.isEmpty() ? 0 : 1);
    AppEventDto created = createEvent(request, creatorUserId);
    try {
      uploadImageFiles(created.id(), images, creatorUserId);
      uploadVideoFile(created.id(), video, creatorUserId);
    } catch (RuntimeException exception) {
      List<EventMediaRow> uploadedMedia = eventMapper.findMediaByEventId(created.id());
      eventMapper.delete(created.id());
      mediaStorageService.deleteStoredObjects(uploadedMedia);
      throw exception;
    }
    return getEventById(created.id(), creatorUserId);
  }


  public AppEventDto updateEvent(String eventId, UpdateEventRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    EventRow current = requireOwnedEvent(eventId, userId);
    String title = firstNonBlank(request.title(), current.getTitleHr());
    String where = firstNonBlank(request.where(), current.getWhereHr());
    String about = firstNonBlank(request.about(), current.getAboutHr());
    OffsetDateTime startAt = resolveUpdatedStartAt(request, current);
    OffsetDateTime endAt = resolveUpdatedEndAt(request, startAt, current);
    String visibility = resolveUpdatedVisibility(request, current);
    String attendanceMode = resolveUpdatedAttendanceMode(request, current);
    BigDecimal priceAmount = resolveUpdatedPriceAmount(request, current, attendanceMode);
    String priceCurrency = resolveUpdatedPriceCurrency(request, current, attendanceMode);
    Integer capacity = firstNonNull(request.capacity(), current.getCapacity());
    CoordinatesDto coordinates = resolveUpdatedCoordinates(request, current);
    List<String> tags = request.tags() == null ? eventDtoMapper.tagsFromRow(current) : normalizeTags(request.tags());
    boolean shouldNotifyParticipants = hasEventUpdateNotificationChange(
        current,
        title,
        where,
        about,
        firstNonBlank(request.address(), current.getAddress()),
        startAt,
        endAt,
        visibility,
        attendanceMode,
        priceAmount,
        priceCurrency,
        capacity,
        coordinates,
        request.entranceCoordinates(),
        request.entryInstructions(),
        normalizeStatus(firstNonBlank(request.status(), current.getStatus())),
        tags);

    validateCommercialFields(attendanceMode, priceAmount, priceCurrency, capacity);
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
    if (request.entranceCoordinates() != null) {
      current.setEntranceLatitude(request.entranceCoordinates().latitude());
      current.setEntranceLongitude(request.entranceCoordinates().longitude());
    }
    current.setEntryInstructionsHr(entryInstructions == null ? null : entryInstructions.hr());
    current.setEntryInstructionsEn(entryInstructions == null ? null : entryInstructions.en());
    current.setVisibility(visibility);
    current.setAttendanceMode(attendanceMode);
    current.setPriceAmount(priceAmount);
    current.setPriceCurrency(priceCurrency);
    current.setCapacity(capacity);
    current.setStatus(normalizeStatus(firstNonBlank(request.status(), current.getStatus())));
    ensureNoExternalMediaForPrivateVisibility(eventId, current.getVisibility());

    eventMapper.update(current);
    syncTags(current.getId(), tags);
    syncTicketProduct(current);
    if (shouldNotifyParticipants) {
      notifyEventUpdated(current, userId);
    }
    return getEventById(eventId, userId);
  }

  private OffsetDateTime resolveUpdatedStartAt(UpdateEventRequest request, EventRow current) {
    String startAtInput = firstNonBlank(request.startAt(), firstNonBlank(request.whenISO(), eventDtoMapper.timestamp(current.getStartAt())));
    return parseWhenIso(startAtInput, FIELD_START_AT);
  }

  private OffsetDateTime resolveUpdatedEndAt(UpdateEventRequest request, OffsetDateTime startAt, EventRow current) {
    OffsetDateTime endAt = request.endAt() == null ? current.getEndAt() : parseOptionalWhenIso(request.endAt(), "endAt");
    if (endAt != null && endAt.isBefore(startAt)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endAt must be after startAt.");
    }
    return endAt;
  }

  private String resolveUpdatedVisibility(UpdateEventRequest request, EventRow current) {
    return request.visibility() == null ? current.getVisibility() : normalizeVisibility(request.visibility());
  }

  private String resolveUpdatedAttendanceMode(UpdateEventRequest request, EventRow current) {
    return request.attendanceMode() == null
        ? current.getAttendanceMode()
        : normalizeAttendanceMode(request.attendanceMode());
  }

  private BigDecimal resolveUpdatedPriceAmount(
      UpdateEventRequest request, EventRow current, String attendanceMode) {
    return ATTENDANCE_MODE_PAID.equals(attendanceMode)
        ? firstNonNull(request.priceAmount(), current.getPriceAmount())
        : null;
  }

  private String resolveUpdatedPriceCurrency(UpdateEventRequest request, EventRow current, String attendanceMode) {
    String rawPriceCurrency = firstNonBlank(request.priceCurrency(), current.getPriceCurrency());
    return ATTENDANCE_MODE_PAID.equals(attendanceMode) && rawPriceCurrency != null
        ? rawPriceCurrency.trim().toUpperCase()
        : null;
  }

  private CoordinatesDto resolveUpdatedCoordinates(UpdateEventRequest request, EventRow current) {
    return request.coordinates() == null
        ? new CoordinatesDto(current.getLatitude(), current.getLongitude())
        : request.coordinates();
  }


  public void deleteEvent(String eventId, String userId) {
    requireOwnedEvent(eventId, userId);
    List<EventMediaRow> media = eventMapper.findMediaByEventId(eventId);
    eventMapper.delete(eventId);
    mediaStorageService.deleteStoredObjects(media);
  }


  public AppEventDto addMedia(String eventId, EventMediaRequest request, String userId) {
    EventRow event = requireOwnedEvent(eventId, userId);
    if (request == null || trimToNull(request.url()) == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required.");
    }
    ensurePublicVisibilityForExternalMedia(event.getVisibility());

    String mediaType = normalizeMediaType(request.mediaType());
    mediaStorageService.ensureMediaCapacity(eventId, mediaType, 1);
    EventMediaRow media = new EventMediaRow();
    media.setId(MEDIA_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
    media.setEventId(eventId);
    media.setMediaType(mediaType);
    media.setUrl(request.url().trim());
    media.setThumbnailUrl(trimToNull(request.thumbnailUrl()));
    media.setSortOrder(eventMapper.findMediaByEventId(eventId).size());
    eventMapper.insertMedia(media);
    eventMapper.touchEvent(eventId);
    return getEventById(eventId, userId);
  }

  public StoredObjectContent getMediaContent(String eventId, String mediaId, String userId) {
    EventRow event = getAccessibleEvent(eventId, userId);
    EventMediaRow media = eventMapper.findMediaById(event.getId(), mediaId);
    if (media == null || trimToNull(media.getStorageKey()) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
    }

    return mediaStorageService.getMediaContent(media);
  }

  public StoredObjectContent getMediaThumbnailContent(String eventId, String mediaId, String userId) {
    EventRow event = getAccessibleEvent(eventId, userId);
    EventMediaRow media = eventMapper.findMediaById(event.getId(), mediaId);
    if (media == null || trimToNull(media.getThumbnailStorageKey()) == null) {
      return getMediaContent(eventId, mediaId, userId);
    }

    return mediaStorageService.getMediaThumbnailContent(media);
  }


  public AppEventDto uploadMedia(String eventId, MultipartFile image, MultipartFile video, String userId) {
    boolean hasImage = image != null && !image.isEmpty();
    boolean hasVideo = video != null && !video.isEmpty();
    if (hasImage == hasVideo) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "upload exactly one image or one video.");
    }
    if (hasImage) {
      uploadImageFiles(eventId, List.of(image), userId);
    } else {
      uploadVideoFile(eventId, video, userId);
    }
    return getEventById(eventId, userId);
  }


  public AppEventDto uploadVideoBytes(String eventId, byte[] bytes, String contentType, String filename, String userId) {
    if (bytes == null || bytes.length == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video is required.");
    }
    requireOwnedEvent(eventId, userId);
    mediaStorageService.validateUploadedVideoCount(1);
    mediaStorageService.ensureMediaCapacity(eventId, MEDIA_TYPE_VIDEO, 1);
    EventMediaRow media =
        mediaStorageService.buildUploadedVideoMedia(eventId, bytes, contentType, filename, eventMapper.findMediaByEventId(eventId).size());
    eventMapper.insertMedia(media);
    eventMapper.touchEvent(eventId);
    return getEventById(eventId, userId);
  }


  public AppEventDto deleteMedia(String eventId, String mediaId, String userId) {
    requireOwnedEvent(eventId, userId);
    List<EventMediaRow> mediaItems = eventMapper.findMediaByEventId(eventId);
    EventMediaRow media = mediaItems.stream()
        .filter(item -> item.getId().equals(mediaId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "media not found."));
    long imageCount = mediaItems.stream().filter(item -> MEDIA_TYPE_IMAGE.equals(item.getMediaType())).count();
    if (MEDIA_TYPE_IMAGE.equals(media.getMediaType()) && imageCount <= 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "event must keep at least one image.");
    }
    eventMapper.deleteMedia(eventId, mediaId);
    eventMapper.touchEvent(eventId);
    mediaStorageService.deleteStoredObject(media);
    return getEventById(eventId, userId);
  }

  public List<EventParticipantDto> getParticipants(String eventId, String userId) {
    requireOwnedEvent(eventId, userId);
    return eventMapper.findParticipantsByEventId(eventId).stream().map(eventDtoMapper::toParticipantDto).toList();
  }

  private void insertUrlMedia(String eventId, List<EventMediaRequest> mediaRequests, String visibility) {
    if (mediaRequests == null || mediaRequests.isEmpty()) {
      return;
    }
    ensurePublicVisibilityForExternalMedia(visibility);
    for (EventMediaRequest mediaRequest : mediaRequests) {
      if (mediaRequest == null || trimToNull(mediaRequest.url()) == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "media.url is required.");
      }
      String mediaType = normalizeMediaType(mediaRequest.mediaType());
      mediaStorageService.ensureMediaCapacity(eventId, mediaType, 1);
      EventMediaRow media = new EventMediaRow();
      media.setId(MEDIA_ID_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
      media.setEventId(eventId);
      media.setMediaType(mediaType);
      media.setUrl(mediaRequest.url().trim());
      media.setThumbnailUrl(trimToNull(mediaRequest.thumbnailUrl()));
      media.setOriginalFilename(mediaStorageService.normalizeOriginalFilename(mediaRequest.fileName()));
      media.setSortOrder(eventMapper.findMediaByEventId(eventId).size());
      eventMapper.insertMedia(media);
    }
    eventMapper.touchEvent(eventId);
  }

  private void uploadImageFiles(String eventId, List<MultipartFile> images, String userId) {
    requireOwnedEvent(eventId, userId);
    mediaStorageService.validateUploadedImageCount(images, false);
    mediaStorageService.ensureMediaCapacity(eventId, MEDIA_TYPE_IMAGE, images.size());
    for (MultipartFile image : images) {
      EventMediaRow media = mediaStorageService.buildUploadedImageMedia(eventId, image, eventMapper.findMediaByEventId(eventId).size());
      eventMapper.insertMedia(media);
    }
    eventMapper.touchEvent(eventId);
  }

  private void uploadVideoFile(String eventId, MultipartFile video, String userId) {
    if (video == null || video.isEmpty()) {
      return;
    }
    requireOwnedEvent(eventId, userId);
    mediaStorageService.validateUploadedVideoCount(1);
    mediaStorageService.ensureMediaCapacity(eventId, MEDIA_TYPE_VIDEO, 1);
    EventMediaRow media = mediaStorageService.buildUploadedVideoMedia(eventId, video, eventMapper.findMediaByEventId(eventId).size());
    eventMapper.insertMedia(media);
    eventMapper.touchEvent(eventId);
  }

  public List<EventParticipantDto> approveParticipant(String eventId, String participantUserId, String userId) {
    requireOwnedEvent(eventId, userId);
    eventMapper.updateParticipantStatus(eventId, participantUserId, PARTICIPANT_STATUS_APPROVED);
    messageService.getOrCreateEventChatRoom(eventId, participantUserId);
    notifyParticipant(
        eventId,
        participantUserId,
        NOTIFICATION_WAITLIST_APPROVED,
        "Dolazak odobren",
        "Attendance approved",
        "Organizator je odobrio tvoj dolazak na event.",
        "The organizer approved your attendance.");
    return getParticipants(eventId, userId);
  }


  public List<EventParticipantDto> removeParticipant(String eventId, String participantUserId, String userId) {
    EventRow event = requireOwnedEvent(eventId, userId);
    if (participantUserId.equals(userId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner cannot be removed from the event.");
    }
    boolean active = eventMapper.findParticipantsByEventId(eventId).stream()
        .anyMatch(participant ->
            participantUserId.equals(participant.getUserId())
                && (PARTICIPANT_STATUS_JOINED.equals(participant.getStatus())
                    || PARTICIPANT_STATUS_APPROVED.equals(participant.getStatus())
                    || PARTICIPANT_STATUS_WAITLISTED.equals(participant.getStatus())));
    eventMapper.updateParticipantStatus(eventId, participantUserId, PARTICIPANT_STATUS_REJECTED);
    if (active) {
      eventMapper.decrementParticipantCount(event.getId());
    }
    messageService.leaveEventChatRoom(eventId, participantUserId);
    notifyParticipant(
        eventId,
        participantUserId,
        "event_attendance_removed",
        "Maknut si s eventa",
        "Removed from event",
        "Organizator te maknuo s eventa.",
        "The organizer removed you from the event.");
    return getParticipants(eventId, userId);
  }


  public List<EventParticipantDto> blockParticipant(String eventId, String participantUserId, String userId) {
    EventRow event = requireOwnedEvent(eventId, userId);
    if (ATTENDANCE_MODE_PAID.equals(event.getAttendanceMode())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Paid events do not support event-level blocking.");
    }
    if (participantUserId.equals(userId)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner cannot be blocked from the event.");
    }

    boolean active = eventMapper.findParticipantsByEventId(eventId).stream()
        .anyMatch(participant ->
            participantUserId.equals(participant.getUserId())
                && (PARTICIPANT_STATUS_JOINED.equals(participant.getStatus())
                    || PARTICIPANT_STATUS_APPROVED.equals(participant.getStatus())
                    || PARTICIPANT_STATUS_WAITLISTED.equals(participant.getStatus())));
    eventMapper.blockUser(eventId, participantUserId, userId);
    eventMapper.updateParticipantStatus(eventId, participantUserId, PARTICIPANT_STATUS_REJECTED);
    if (active) {
      eventMapper.decrementParticipantCount(event.getId());
    }
    messageService.leaveEventChatRoom(eventId, participantUserId);
    notifyParticipant(
        eventId,
        participantUserId,
        "event_attendance_blocked",
        "Blokiran si za event",
        "Blocked from event",
        "Organizator te blokirao za ovaj event.",
        "The organizer blocked you from this event.");
    return getParticipants(eventId, userId);
  }

  public AppEventDto toDto(EventRow row) {
    return eventDtoMapper.toDto(row);
  }

  private List<AppEventDto> toDtosWithMedia(List<EventRow> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }

    Map<String, List<EventMediaDto>> mediaByEventId = loadMediaByEventIds(rows);
    return rows.stream().map(row -> eventDtoMapper.toDto(row, mediaByEventId.get(row.getId()))).toList();
  }

  private Map<String, List<EventMediaDto>> loadMediaByEventIds(List<EventRow> rows) {
    List<String> eventIds = rows.stream().map(EventRow::getId).distinct().toList();
    Map<String, List<EventMediaDto>> mediaByEventId = new HashMap<>();

    for (EventMediaRow mediaRow : eventMapper.findMediaByEventIds(eventIds)) {
      mediaByEventId.computeIfAbsent(mediaRow.getEventId(), _ -> new java.util.ArrayList<>()).add(eventDtoMapper.toMediaDto(mediaRow));
    }

    return mediaByEventId;
  }

  private void ensureNoExternalMediaForPrivateVisibility(String eventId, String visibility) {
    if (!VISIBILITY_FRIENDS.equals(visibility)) {
      return;
    }
    boolean hasExternalMedia = eventMapper.findMediaByEventId(eventId).stream()
        .anyMatch(media -> trimToNull(media.getStorageKey()) == null);
    if (hasExternalMedia) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Friends-only events can only use uploaded media because external URL media cannot be privacy protected.");
    }
  }

  private void ensurePublicVisibilityForExternalMedia(String visibility) {
    if (VISIBILITY_FRIENDS.equals(visibility)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Friends-only events require uploaded media; external media URLs cannot be privacy protected.");
    }
  }

  private EventRow requireJoinableEvent(String eventId, String userId, boolean requirePaidReceipt) {
    EventRow row = getAccessibleEvent(eventId, userId);
    if (!EventRules.canJoin(row)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event cannot be joined.");
    }
    if (requirePaidReceipt
        && ATTENDANCE_MODE_PAID.equals(row.getAttendanceMode())
        && !EventRules.isJoinedByMe(row.getUserParticipantStatus())
        && !eventTicketService.hasCompletedTicketPayment(eventId, userId)) {
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


  private boolean isActiveAttendance(AppEventDto event) {
    return PARTICIPANT_STATUS_JOINED.equals(event.attendanceStatus()) || PARTICIPANT_STATUS_APPROVED.equals(event.attendanceStatus());
  }

  private OffsetDateTime eventStart(AppEventDto event) {
    return parseWhenIso(firstNonBlank(event.startAt(), event.whenISO()), FIELD_START_AT);
  }

  public void markPastEventsFinished() {
    eventMapper.markPastEventsFinished();
  }

  public void sendEventReminders() {
    OffsetDateTime now = OffsetDateTime.now();
    sendEventReminderWindow(NOTIFICATION_REMINDER_WEEK, now.plusDays(7), now.plusDays(7).plusHours(1));
    sendEventReminderWindow(NOTIFICATION_REMINDER_DAY, now.plusDays(1), now.plusDays(1).plusHours(1));
  }

  private void sendEventReminderWindow(String notificationType, OffsetDateTime from, OffsetDateTime to) {
    List<EventNotificationRecipientRow> candidates = eventMapper.findReminderNotificationCandidates(notificationType, from, to);
    for (EventNotificationRecipientRow candidate : candidates) {
      boolean weekReminder = NOTIFICATION_REMINDER_WEEK.equals(notificationType);
      String titleHr = weekReminder ? "Event je za tjedan dana" : "Event je sutra";
      String titleEn = weekReminder ? "Event is in one week" : "Event is tomorrow";
      String bodyHr = firstNonBlank(candidate.getTitleHr(), EVENT_TITLE_FALLBACK);
      String bodyEn = firstNonBlank(candidate.getTitleEn(), firstNonBlank(candidate.getTitleHr(), EVENT_TITLE_FALLBACK));
      int inserted = eventMapper.insertNotificationIfAbsent(
          notificationId(),
          candidate.getUserId(),
          notificationType,
          titleHr,
          bodyHr,
          candidate.getEventId());
      if (inserted > 0) {
        notificationService.sendEventPush(
            List.of(candidate.getUserId()),
            notificationType,
            candidate.getEventId(),
            titleHr,
            titleEn,
            bodyHr,
            bodyEn);
      }
    }
  }

  private void notifyParticipant(String eventId, String userId, String type, String titleHr, String titleEn, String bodyHr, String bodyEn) {
    eventMapper.insertNotification(
        notificationId(),
        userId,
        type,
        titleHr,
        bodyHr,
        eventId);
    notificationService.sendEventPush(List.of(userId), type, eventId, titleHr, titleEn, bodyHr, bodyEn);
  }

  private void notifyEventUpdated(EventRow event, String updaterUserId) {
    List<EventNotificationRecipientRow> recipients =
        eventMapper.findActiveNotificationRecipientsForEvent(event.getId(), updaterUserId);
    if (recipients.isEmpty()) {
      return;
    }

    String titleHr = "Event je promijenjen";
    String titleEn = "Event changed";
    String bodyHr = firstNonBlank(event.getTitleHr(), EVENT_TITLE_FALLBACK) + " ima nove detalje.";
    String bodyEn = firstNonBlank(event.getTitleEn(), firstNonBlank(event.getTitleHr(), EVENT_TITLE_FALLBACK)) + " has updated details.";
    List<String> userIds = recipients.stream().map(EventNotificationRecipientRow::getUserId).distinct().toList();

    for (String recipientUserId : userIds) {
      eventMapper.insertNotification(notificationId(), recipientUserId, NOTIFICATION_EVENT_UPDATED, titleHr, bodyHr, event.getId());
    }
    notificationService.sendEventPush(userIds, NOTIFICATION_EVENT_UPDATED, event.getId(), titleHr, titleEn, bodyHr, bodyEn);
  }

  private boolean hasEventUpdateNotificationChange(
      EventRow current,
      String title,
      String where,
      String about,
      String address,
      OffsetDateTime startAt,
      OffsetDateTime endAt,
      String visibility,
      String attendanceMode,
      BigDecimal priceAmount,
      String priceCurrency,
      Integer capacity,
      CoordinatesDto coordinates,
      CoordinatesDto entranceCoordinates,
      String entryInstructions,
      String status,
      List<String> tags) {
    return !Objects.equals(trimToNull(current.getTitleHr()), trimToNull(title))
        || !Objects.equals(trimToNull(current.getWhereHr()), trimToNull(where))
        || !Objects.equals(trimToNull(current.getAboutHr()), trimToNull(about))
        || !Objects.equals(trimToNull(current.getAddress()), trimToNull(address))
        || !Objects.equals(current.getStartAt(), startAt)
        || !Objects.equals(current.getEndAt(), endAt)
        || !Objects.equals(trimToNull(current.getVisibility()), trimToNull(visibility))
        || !Objects.equals(trimToNull(current.getAttendanceMode()), trimToNull(attendanceMode))
        || !sameMoney(current.getPriceAmount(), priceAmount)
        || !Objects.equals(trimToNull(current.getPriceCurrency()), trimToNull(priceCurrency))
        || !Objects.equals(current.getCapacity(), capacity)
        || !sameCoordinate(current.getLatitude(), current.getLongitude(), coordinates)
        || (entranceCoordinates != null && !sameCoordinate(current.getEntranceLatitude(), current.getEntranceLongitude(), entranceCoordinates))
        || (entryInstructions != null && !Objects.equals(trimToNull(current.getEntryInstructionsHr()), trimToNull(entryInstructions)))
        || !Objects.equals(trimToNull(current.getStatus()), trimToNull(status))
        || !Objects.equals(eventDtoMapper.tagsFromRow(current), tags);
  }

  private boolean sameCoordinate(Double latitude, Double longitude, CoordinatesDto coordinates) {
    if (coordinates == null) {
      return latitude == null && longitude == null;
    }
    return Objects.equals(latitude, coordinates.latitude()) && Objects.equals(longitude, coordinates.longitude());
  }

  private boolean sameMoney(BigDecimal left, BigDecimal right) {
    if (left == null || right == null) {
      return left == null && right == null;
    }
    return left.compareTo(right) == 0;
  }

  private String notificationId() {
    return "notif-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
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
    requireNonBlank(firstNonBlank(request.startAt(), request.whenISO()), FIELD_START_AT);

    if (request.coordinates() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "coordinates are required.");
    }

    validateCoordinates(request.coordinates(), "coordinates");
    if (request.entranceCoordinates() != null) {
      validateCoordinates(request.entranceCoordinates(), "entranceCoordinates");
    }
    normalizeTags(request.tags());
  }

  private void syncTags(String eventId, List<String> tags) {
    eventMapper.deleteTags(eventId);
    for (String tag : tags) {
      eventMapper.insertTag(eventId, tag);
    }
  }

  private List<String> normalizeTags(List<String> rawTags) {
    if (rawTags == null) {
      return List.of();
    }

    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    Map<String, Integer> categoryCounts = new HashMap<>();
    for (String rawTag : rawTags) {
      EventTagCatalog.TagDefinition tag = normalizeTag(rawTag);
      if (tag != null && normalized.add(tag.tag())) {
        int nextCategoryCount = categoryCounts.getOrDefault(tag.categoryId(), 0) + 1;
        if (nextCategoryCount > EventTagCatalog.MAX_TAGS_PER_CATEGORY) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "events accept up to " + EventTagCatalog.MAX_TAGS_PER_CATEGORY + " tags per category.");
        }
        categoryCounts.put(tag.categoryId(), nextCategoryCount);
      }
    }

    if (normalized.size() > MAX_EVENT_TAGS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "events accept up to " + MAX_EVENT_TAGS + " tags.");
    }

    return new ArrayList<>(normalized);
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

  private List<String> normalizeFilterTags(String rawTags) {
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

  private List<String> normalizeAttendanceModeFilter(String rawAttendanceModes) {
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

  private String normalizeFeedPreferenceType(String type) {
    String normalized = trimToNull(type);
    if (FEED_PREFERENCE_TYPE_EVENT.equals(normalized) || "creator".equals(normalized) || "tag".equals(normalized)) {
      return normalized;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported feed preference type.");
  }

  private String normalizeFeedPreferenceTarget(String type, String targetId) {
    String normalized = trimToNull(targetId);
    if (normalized == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "targetId is required.");
    }
    return "tag".equals(type) ? normalized.replaceFirst("^#+", "").trim().toLowerCase(Locale.ROOT) : normalized;
  }

  private String trimLabel(String label, int maxLength) {
    String normalized = trimToNull(label);
    if (normalized == null) {
      return "";
    }
    return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
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
    } catch (DateTimeParseException _) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + " format.");
    }
  }

  private OffsetDateTime parseOptionalWhenIso(String whenIso, String fieldName) {
    String normalized = trimToNull(whenIso);
    return normalized == null ? null : parseWhenIso(normalized, fieldName);
  }

  private String normalizeVisibility(String visibility) {
    if (VISIBILITY_FRIENDS.equalsIgnoreCase(visibility) || "private".equalsIgnoreCase(visibility)) {
      return VISIBILITY_FRIENDS;
    }

    return DEFAULT_VISIBILITY;
  }

  private String normalizeAttendanceMode(String attendanceMode) {
    if (ATTENDANCE_MODE_WAITLIST.equalsIgnoreCase(attendanceMode)) {
      return ATTENDANCE_MODE_WAITLIST;
    }

    if (ATTENDANCE_MODE_PAID.equalsIgnoreCase(attendanceMode)) {
      return ATTENDANCE_MODE_PAID;
    }

    return DEFAULT_ATTENDANCE_MODE;
  }

  private String normalizeStatus(String status) {
    String normalized = trimToNull(status);
    if (normalized == null) {
      return DEFAULT_STATUS;
    }

    if (STATUS_DRAFT.equalsIgnoreCase(normalized)
        || STATUS_PUBLISHED.equalsIgnoreCase(normalized)
        || STATUS_CANCELLED.equalsIgnoreCase(normalized)
        || STATUS_FINISHED.equalsIgnoreCase(normalized)) {
      return normalized.toLowerCase();
    }

    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported event status.");
  }

  private String normalizeMediaType(String mediaType) {
    String normalized = trimToNull(mediaType);
    if (normalized == null || MEDIA_TYPE_IMAGE.equalsIgnoreCase(normalized)) {
      return MEDIA_TYPE_IMAGE;
    }
    if (MEDIA_TYPE_VIDEO.equalsIgnoreCase(normalized)) {
      return MEDIA_TYPE_VIDEO;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported media type.");
  }

  private void validateCommercialFields(String attendanceMode, BigDecimal priceAmount, String priceCurrency, Integer capacity) {
    if (capacity != null && capacity <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity must be greater than 0.");
    }

    if (!ATTENDANCE_MODE_PAID.equals(attendanceMode)) {
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
    if (!ATTENDANCE_MODE_PAID.equals(row.getAttendanceMode())) {
      return;
    }

    eventTicketService.syncTicketProductForEvent(row);
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

  private record LocalizedInput(String hr, String en) {}
}
