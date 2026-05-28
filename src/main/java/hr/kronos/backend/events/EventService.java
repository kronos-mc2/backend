package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CoordinatesDto;
import hr.kronos.backend.api.dto.CreateFeedPreferenceRequest;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.EventMediaRequest;
import hr.kronos.backend.api.dto.EventMediaDto;
import hr.kronos.backend.api.dto.EventParticipantDto;
import hr.kronos.backend.api.dto.EventRatingRequest;
import hr.kronos.backend.api.dto.FeedImpressionRequest;
import hr.kronos.backend.api.dto.FeedPreferenceDto;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.LocalizedTextDto;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import hr.kronos.backend.api.dto.SavedEventsOverviewDto;
import hr.kronos.backend.api.dto.UpdateEventRequest;
import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventMediaRow;
import hr.kronos.backend.events.persistence.EventParticipantRow;
import hr.kronos.backend.events.persistence.EventRow;
import hr.kronos.backend.events.persistence.FeedPreferenceRow;
import hr.kronos.backend.messages.MessageService;
import hr.kronos.backend.payments.persistence.PaymentMapper;
import hr.kronos.backend.storage.ObjectStorageProperties;
import hr.kronos.backend.storage.ObjectStorageService;
import hr.kronos.backend.storage.StoredObjectContent;
import hr.kronos.backend.storage.StoredObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {
  private static final String CREATED_EVENT_TYPE = "created";
  private static final String STATUS_PUBLISHED = "published";
  private static final String DEFAULT_STATUS = STATUS_PUBLISHED;
  private static final String DEFAULT_ATTENDANCE_MODE = "open";
  private static final String DEFAULT_VISIBILITY = "public";
  private static final String VISIBILITY_FRIENDS = "friends";
  private static final String FIELD_START_AT = "startAt";
  private static final String ATTENDANCE_MODE_PAID = "paid";
  private static final String ATTENDANCE_MODE_WAITLIST = "waitlist";
  private static final String MEDIA_TYPE_IMAGE = "image";
  private static final String MEDIA_TYPE_VIDEO = "video";
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
  private static final int DEFAULT_FEED_LIMIT = 5;
  private static final int MAX_FEED_LIMIT = 10;
  private static final int SAVED_COLLECTION_PREVIEW_LIMIT = 12;
  private static final int SAVED_GOING_SOON_LIMIT = 3;
  private static final int SAVED_PAST_LIMIT = 3;
  private static final int MAX_FEED_SEED_LENGTH = 80;
  private static final int MAX_EVENT_TAGS = 10;
  private static final int MAX_EVENT_IMAGES = 5;
  private static final int MAX_EVENT_VIDEOS = 1;
  private static final String CONTENT_TYPE_JPEG = "image/jpeg";
  private static final String CONTENT_TYPE_PNG = "image/png";
  private static final String CONTENT_TYPE_MP4 = "video/mp4";
  private static final String CONTENT_TYPE_QUICKTIME = "video/quicktime";
  private static final String CONTENT_TYPE_M4V = "video/x-m4v";

  private final EventMapper eventMapper;
  private final MessageService messageService;
  private final PaymentMapper paymentMapper;
  private final ObjectStorageService objectStorageService;
  private final ObjectStorageProperties objectStorageProperties;

  public EventService(
      EventMapper eventMapper,
      MessageService messageService,
      PaymentMapper paymentMapper,
      ObjectStorageService objectStorageService,
      ObjectStorageProperties objectStorageProperties) {
    this.eventMapper = eventMapper;
    this.messageService = messageService;
    this.paymentMapper = paymentMapper;
    this.objectStorageService = objectStorageService;
    this.objectStorageProperties = objectStorageProperties;
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
    List<String> normalizedTags = normalizeFilterTags(tags);
    List<String> normalizedAttendanceModes = normalizeAttendanceModeFilter(attendanceModes);
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

  public FeedPageDto getFeed(String userId, String cursor, Integer limit, String seed) {
    markPastEventsFinished();
    int offset = parseFeedOffset(cursor);
    int normalizedLimit = normalizeFeedLimit(limit);
    String normalizedSeed = normalizeFeedSeed(seed);
    List<EventRow> rows =
        eventMapper.findFeedPage(
            userId,
            normalizedSeed,
            offset,
            normalizedLimit + 1);

    boolean hasMore = rows.size() > normalizedLimit;
    List<EventRow> pageRows = hasMore ? rows.subList(0, normalizedLimit) : rows;
    String nextCursor = hasMore ? encodeFeedCursor(offset + pageRows.size()) : null;
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
    return toDto(row, eventMapper.findMediaByEventId(row.getId()).stream().map(this::toMediaDto).toList());
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
    return eventMapper.findFeedPreferences(userId).stream().map(this::toFeedPreferenceDto).toList();
  }

  public FeedPreferenceDto createFeedPreference(CreateFeedPreferenceRequest request, String userId) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }

    String type = normalizeFeedPreferenceType(request.type());
    String targetId = normalizeFeedPreferenceTarget(type, request.targetId());
    if ("event".equals(type)) {
      getAccessibleEvent(targetId, userId);
    }
    String label = firstNonBlank(request.label(), targetId);
    String id = "feed-block-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    eventMapper.insertFeedPreference(id, userId, type, targetId, trimLabel(label, 180));
    if ("event".equals(type)) {
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
    validateUploadedImageCount(images, true);
    validateUploadedVideoCount(video == null || video.isEmpty() ? 0 : 1);
    AppEventDto created = createEvent(request, creatorUserId);
    try {
      uploadImageFiles(created.id(), images, creatorUserId);
      uploadVideoFile(created.id(), video, creatorUserId);
    } catch (RuntimeException exception) {
      List<EventMediaRow> uploadedMedia = eventMapper.findMediaByEventId(created.id());
      eventMapper.delete(created.id());
      deleteStoredObjects(uploadedMedia);
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
    List<String> tags = request.tags() == null ? tagsFromRow(current) : normalizeTags(request.tags());

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
    ensureNoExternalMediaForPrivateVisibility(eventId, current.getVisibility());

    eventMapper.update(current);
    syncTags(current.getId(), tags);
    syncTicketProduct(current);
    return getEventById(eventId, userId);
  }

  private OffsetDateTime resolveUpdatedStartAt(UpdateEventRequest request, EventRow current) {
    String startAtInput = firstNonBlank(request.startAt(), firstNonBlank(request.whenISO(), timestamp(current.getStartAt())));
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
    deleteStoredObjects(media);
  }

  public AppEventDto addMedia(String eventId, EventMediaRequest request, String userId) {
    EventRow event = requireOwnedEvent(eventId, userId);
    if (request == null || trimToNull(request.url()) == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url is required.");
    }
    ensurePublicVisibilityForExternalMedia(event.getVisibility());

    String mediaType = normalizeMediaType(request.mediaType());
    ensureMediaCapacity(eventId, mediaType, 1);
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

  public StoredObjectContent getMediaContent(String eventId, String mediaId, String userId) {
    EventRow event = getAccessibleEvent(eventId, userId);
    EventMediaRow media = eventMapper.findMediaById(event.getId(), mediaId);
    if (media == null || trimToNull(media.getStorageKey()) == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
    }

    StoredObjectContent content = objectStorageService.get(resolveBucketName(media), media.getStorageKey());
    String contentType = firstNonBlank(media.getContentType(), firstNonBlank(content.contentType(), defaultContentType(media.getMediaType())));
    return new StoredObjectContent(content.bytes(), contentType, content.contentLength());
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
    validateUploadedVideoCount(1);
    ensureMediaCapacity(eventId, MEDIA_TYPE_VIDEO, 1);
    EventMediaRow media =
        buildUploadedVideoMedia(eventId, bytes, contentType, filename, eventMapper.findMediaByEventId(eventId).size());
    eventMapper.insertMedia(media);
    return getEventById(eventId, userId);
  }

  public AppEventDto deleteMedia(String eventId, String mediaId, String userId) {
    requireOwnedEvent(eventId, userId);
    List<EventMediaRow> mediaItems = eventMapper.findMediaByEventId(eventId);
    EventMediaRow media = mediaItems.stream()
        .filter(item -> item.getId().equals(mediaId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "media not found."));
    if (MEDIA_TYPE_IMAGE.equals(media.getMediaType()) && countMediaItems(mediaItems, MEDIA_TYPE_IMAGE) <= 1) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "event must keep at least one image.");
    }
    eventMapper.deleteMedia(eventId, mediaId);
    deleteStoredObject(media);
    return getEventById(eventId, userId);
  }

  public List<EventParticipantDto> getParticipants(String eventId, String userId) {
    requireOwnedEvent(eventId, userId);
    return eventMapper.findParticipantsByEventId(eventId).stream().map(this::toParticipantDto).toList();
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
      ensureMediaCapacity(eventId, mediaType, 1);
      EventMediaRow media = new EventMediaRow();
      media.setId("media-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
      media.setEventId(eventId);
      media.setMediaType(mediaType);
      media.setUrl(mediaRequest.url().trim());
      media.setThumbnailUrl(trimToNull(mediaRequest.thumbnailUrl()));
      media.setOriginalFilename(normalizeOriginalFilename(mediaRequest.fileName()));
      media.setSortOrder(eventMapper.findMediaByEventId(eventId).size());
      eventMapper.insertMedia(media);
    }
  }

  private void uploadImageFiles(String eventId, List<MultipartFile> images, String userId) {
    requireOwnedEvent(eventId, userId);
    validateUploadedImageCount(images, false);
    ensureMediaCapacity(eventId, MEDIA_TYPE_IMAGE, images.size());
    for (MultipartFile image : images) {
      EventMediaRow media = buildUploadedImageMedia(eventId, image, eventMapper.findMediaByEventId(eventId).size());
      eventMapper.insertMedia(media);
    }
  }

  private void uploadVideoFile(String eventId, MultipartFile video, String userId) {
    if (video == null || video.isEmpty()) {
      return;
    }
    requireOwnedEvent(eventId, userId);
    validateUploadedVideoCount(1);
    ensureMediaCapacity(eventId, MEDIA_TYPE_VIDEO, 1);
    EventMediaRow media = buildUploadedVideoMedia(eventId, video, eventMapper.findMediaByEventId(eventId).size());
    eventMapper.insertMedia(media);
  }

  private EventMediaRow buildUploadedImageMedia(String eventId, MultipartFile file, int sortOrder) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image is required.");
    }
    try {
      byte[] bytes = file.getBytes();
      validateImageFileSize(bytes.length);
      String contentType = normalizeImageContentType(file.getContentType(), file.getOriginalFilename());
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image must be a readable JPEG or PNG file.");
      }
      validateImageDimensions(image.getWidth(), image.getHeight());
      ensureStorageQuota(bytes.length);
      StoredObject storedObject = objectStorageService.putImage(
          buildStorageKey(eventId, MEDIA_TYPE_IMAGE, contentType),
          bytes,
          contentType,
          image.getWidth(),
          image.getHeight());

      EventMediaRow media = new EventMediaRow();
      media.setId("media-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
      media.setEventId(eventId);
      media.setMediaType(MEDIA_TYPE_IMAGE);
      media.setUrl(storedObject.url());
      media.setThumbnailUrl(storedObject.url());
      media.setOriginalFilename(normalizeOriginalFilename(file.getOriginalFilename()));
      media.setStorageKey(storedObject.storageKey());
      media.setBucketName(storedObject.bucketName());
      media.setContentType(storedObject.contentType());
      media.setByteSize(storedObject.byteSize());
      media.setWidth(storedObject.width());
      media.setHeight(storedObject.height());
      media.setSortOrder(sortOrder);
      return media;
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image could not be read.", exception);
    }
  }

  private EventMediaRow buildUploadedVideoMedia(String eventId, MultipartFile file, int sortOrder) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video is required.");
    }
    try {
      return buildUploadedVideoMedia(eventId, file.getBytes(), file.getContentType(), file.getOriginalFilename(), sortOrder);
    } catch (IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video could not be read.", exception);
    }
  }

  private EventMediaRow buildUploadedVideoMedia(
      String eventId,
      byte[] bytes,
      String rawContentType,
      String originalFilename,
      int sortOrder) {
    validateVideoFileSize(bytes.length);
    String contentType = normalizeVideoContentType(rawContentType, originalFilename);
    ensureStorageQuota(bytes.length);
    StoredObject storedObject = objectStorageService.putObject(
        buildStorageKey(eventId, MEDIA_TYPE_VIDEO, contentType),
        bytes,
        contentType);

    EventMediaRow media = new EventMediaRow();
    media.setId("media-" + UUID.randomUUID().toString().replace("-", "").substring(0, 18));
    media.setEventId(eventId);
    media.setMediaType(MEDIA_TYPE_VIDEO);
    media.setUrl(storedObject.url());
    media.setOriginalFilename(normalizeOriginalFilename(originalFilename));
    media.setStorageKey(storedObject.storageKey());
    media.setBucketName(storedObject.bucketName());
    media.setContentType(storedObject.contentType());
    media.setByteSize(storedObject.byteSize());
    media.setSortOrder(sortOrder);
    return media;
  }

  private void validateUploadedImageCount(List<MultipartFile> images, boolean requireAtLeastOne) {
    int count = images == null ? 0 : images.size();
    if (requireAtLeastOne && count == 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one image is required.");
    }
    if (count > MAX_EVENT_IMAGES) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "events accept up to " + MAX_EVENT_IMAGES + " images.");
    }
  }

  private void validateUploadedVideoCount(int count) {
    if (count > MAX_EVENT_VIDEOS) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "events accept up to one video.");
    }
  }

  private void ensureMediaCapacity(String eventId, String mediaType, int incomingCount) {
    if (incomingCount <= 0) {
      return;
    }
    int currentCount = countMediaItems(eventMapper.findMediaByEventId(eventId), mediaType);
    int maxCount = MEDIA_TYPE_VIDEO.equals(mediaType) ? MAX_EVENT_VIDEOS : MAX_EVENT_IMAGES;
    if (currentCount + incomingCount > maxCount) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          MEDIA_TYPE_VIDEO.equals(mediaType)
              ? "events accept up to one video."
              : "events accept up to " + MAX_EVENT_IMAGES + " images.");
    }
  }

  private void validateImageFileSize(int byteSize) {
    if (byteSize <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image is empty.");
    }
    if (byteSize > objectStorageProperties.getMaxFileBytes()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image must be 5 MB or smaller.");
    }
  }

  private void validateVideoFileSize(int byteSize) {
    if (byteSize <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video is empty.");
    }
    if (byteSize > objectStorageProperties.getMaxVideoBytes()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video must be 10 MB or smaller.");
    }
  }

  private void validateImageDimensions(int width, int height) {
    if (width < objectStorageProperties.getMinImageWidth() || height < objectStorageProperties.getMinImageHeight()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "image resolution must be at least "
              + objectStorageProperties.getMinImageWidth()
              + "x"
              + objectStorageProperties.getMinImageHeight()
              + ".");
    }
    if (width > objectStorageProperties.getMaxImageWidth() || height > objectStorageProperties.getMaxImageHeight()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image resolution is too large.");
    }
  }

  private void ensureStorageQuota(int incomingBytes) {
    long usedBytes = eventMapper.sumStoredMediaBytes();
    if (usedBytes + incomingBytes > objectStorageProperties.getMaxTotalBytes()) {
      throw new ResponseStatusException(HttpStatusCode.valueOf(507), "media storage quota is full.");
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
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image must be JPEG or PNG.");
  }

  private String normalizeVideoContentType(String contentType, String filename) {
    String normalized = trimToNull(contentType);
    if (CONTENT_TYPE_MP4.equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_MP4;
    }
    if (CONTENT_TYPE_QUICKTIME.equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_QUICKTIME;
    }
    if (CONTENT_TYPE_M4V.equalsIgnoreCase(normalized)) {
      return CONTENT_TYPE_M4V;
    }
    String lowerFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
    if (lowerFilename.endsWith(".mp4")) {
      return CONTENT_TYPE_MP4;
    }
    if (lowerFilename.endsWith(".mov") || lowerFilename.endsWith(".qt")) {
      return CONTENT_TYPE_QUICKTIME;
    }
    if (lowerFilename.endsWith(".m4v")) {
      return CONTENT_TYPE_M4V;
    }
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "video must be MP4, MOV, or M4V.");
  }

  private String buildStorageKey(String eventId, String mediaType, String contentType) {
    String extension = storageExtension(mediaType, contentType);
    return "events/" + eventId + "/" + mediaType + "/" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
  }

  private String storageExtension(String mediaType, String contentType) {
    if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
      if (CONTENT_TYPE_QUICKTIME.equals(contentType)) {
        return "mov";
      }
      if (CONTENT_TYPE_M4V.equals(contentType)) {
        return "m4v";
      }
      return "mp4";
    }
    return CONTENT_TYPE_PNG.equals(contentType) ? "png" : "jpg";
  }

  private void deleteStoredObjects(List<EventMediaRow> media) {
    for (EventMediaRow mediaRow : media) {
      deleteStoredObject(mediaRow);
    }
  }

  private void deleteStoredObject(EventMediaRow media) {
    if (media == null || trimToNull(media.getBucketName()) == null || trimToNull(media.getStorageKey()) == null) {
      return;
    }
    objectStorageService.delete(media.getBucketName(), media.getStorageKey());
  }

  public List<EventParticipantDto> approveParticipant(String eventId, String participantUserId, String userId) {
    requireOwnedEvent(eventId, userId);
    eventMapper.updateParticipantStatus(eventId, participantUserId, PARTICIPANT_STATUS_APPROVED);
    notifyParticipant(eventId, participantUserId, "event_attendance_approved", "Dolazak odobren", "Organizator je odobrio tvoj dolazak na event.");
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
    notifyParticipant(eventId, participantUserId, "event_attendance_removed", "Maknut si s eventa", "Organizator te maknuo s eventa.");
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
        row.getSourceUrl(),
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
        tagsFromRow(row),
        media == null || media.isEmpty() ? null : media);
  }

  private FeedPreferenceDto toFeedPreferenceDto(FeedPreferenceRow row) {
    return new FeedPreferenceDto(
        row.getId(),
        row.getBlockType(),
        row.getTargetId(),
        row.getTargetLabel(),
        timestamp(row.getCreatedAt()));
  }

  private List<AppEventDto> toDtosWithMedia(List<EventRow> rows) {
    if (rows.isEmpty()) {
      return List.of();
    }

    Map<String, List<EventMediaDto>> mediaByEventId = loadMediaByEventIds(rows);
    return rows.stream().map(row -> toDto(row, mediaByEventId.get(row.getId()))).toList();
  }

  private EventMediaDto toMediaDto(EventMediaRow row) {
    String url = mediaUrl(row, row.getUrl());
    String thumbnailUrl = mediaUrl(row, firstNonBlank(row.getThumbnailUrl(), row.getUrl()));
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

  private Map<String, List<EventMediaDto>> loadMediaByEventIds(List<EventRow> rows) {
    List<String> eventIds = rows.stream().map(EventRow::getId).distinct().toList();
    Map<String, List<EventMediaDto>> mediaByEventId = new HashMap<>();

    for (EventMediaRow mediaRow : eventMapper.findMediaByEventIds(eventIds)) {
      mediaByEventId.computeIfAbsent(mediaRow.getEventId(), _ -> new java.util.ArrayList<>()).add(toMediaDto(mediaRow));
    }

    return mediaByEventId;
  }

  private String mediaUrl(EventMediaRow row, String fallbackUrl) {
    if (trimToNull(row.getStorageKey()) != null) {
      String contentPath = "/api/events/" + row.getEventId() + "/media/" + row.getId() + "/content";
      return MEDIA_TYPE_VIDEO.equals(row.getMediaType())
          ? contentPath + "/" + mediaContentFilename(row)
          : contentPath;
    }
    return fallbackUrl;
  }

  private String mediaContentFilename(EventMediaRow row) {
    String filename = normalizeOriginalFilename(row.getOriginalFilename());
    String extension = storageExtension(row.getMediaType(), firstNonBlank(row.getContentType(), defaultContentType(row.getMediaType())));
    if (filename == null) {
      return "video." + extension;
    }
    String safeFilename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
    return safeFilename.contains(".") ? safeFilename : safeFilename + "." + extension;
  }

  private String resolveBucketName(EventMediaRow media) {
    return firstNonBlank(media.getBucketName(), objectStorageProperties.getBucket());
  }

  private String defaultContentType(String mediaType) {
    if (MEDIA_TYPE_IMAGE.equals(mediaType)) {
      return CONTENT_TYPE_JPEG;
    }
    if (MEDIA_TYPE_VIDEO.equals(mediaType)) {
      return CONTENT_TYPE_MP4;
    }
    return "application/octet-stream";
  }

  private int countMediaItems(List<EventMediaRow> mediaItems, String mediaType) {
    return (int) mediaItems.stream().filter(item -> mediaType.equals(item.getMediaType())).count();
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
    if (!canJoin(row)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Event cannot be joined.");
    }
    if (requirePaidReceipt
        && ATTENDANCE_MODE_PAID.equals(row.getAttendanceMode())
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
    if (isEventEnded(row)) {
      return false;
    }

    Integer capacity = row.getCapacity();
    if (isJoinedByMe(row.getUserParticipantStatus())) {
      return true;
    }

    if ("blocked".equals(row.getUserParticipantStatus()) || PARTICIPANT_STATUS_REJECTED.equals(row.getUserParticipantStatus())) {
      return false;
    }

    if (ATTENDANCE_MODE_WAITLIST.equals(row.getAttendanceMode())) {
      return true;
    }

    return capacity == null || row.getParticipantCount() < capacity;
  }

  private boolean isEventEnded(EventRow row) {
    OffsetDateTime endAt = row.getEndAt() == null ? row.getStartAt() : row.getEndAt();
    if (endAt == null) {
      endAt = row.getWhenIso();
    }
    return endAt != null && endAt.isBefore(OffsetDateTime.now());
  }

  private boolean isJoinedByMe(String status) {
    return PARTICIPANT_STATUS_JOINED.equals(status) || PARTICIPANT_STATUS_APPROVED.equals(status) || PARTICIPANT_STATUS_WAITLISTED.equals(status);
  }

  private boolean isActiveAttendance(AppEventDto event) {
    return PARTICIPANT_STATUS_JOINED.equals(event.attendanceStatus()) || PARTICIPANT_STATUS_APPROVED.equals(event.attendanceStatus());
  }

  private OffsetDateTime eventStart(AppEventDto event) {
    return parseWhenIso(firstNonBlank(event.startAt(), event.whenISO()), FIELD_START_AT);
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
    if (!PARTICIPANT_STATUS_JOINED.equals(row.getUserParticipantStatus()) && !PARTICIPANT_STATUS_APPROVED.equals(row.getUserParticipantStatus())) {
      return false;
    }
    if (STATUS_FINISHED.equals(row.getStatus())) {
      return true;
    }

    return isEventEnded(row);
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

  private int parseFeedOffset(String cursor) {
    String normalized = trimToNull(cursor);
    if (normalized == null) {
      return 0;
    }

    try {
      String decoded = new String(Base64.getUrlDecoder().decode(normalized), StandardCharsets.UTF_8);
      int offset = Integer.parseInt(decoded);
      if (offset < 0) {
        throw new IllegalArgumentException("Invalid feed cursor.");
      }
      return offset;
    } catch (IllegalArgumentException _) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor format.");
    }
  }

  private String encodeFeedCursor(int offset) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
  }

  private String normalizeFeedSeed(String seed) {
    String normalized = trimToNull(seed);
    if (normalized == null) {
      return "feed-" + OffsetDateTime.now().toInstant().toEpochMilli();
    }

    return normalized.length() <= MAX_FEED_SEED_LENGTH ? normalized : normalized.substring(0, MAX_FEED_SEED_LENGTH);
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

  private List<String> tagsFromRow(EventRow row) {
    String tagsCsv = trimToNull(row.getTagsCsv());
    if (tagsCsv == null) {
      return List.of();
    }

    return List.of(tagsCsv.split(",")).stream().map(this::trimToNull).filter(Objects::nonNull).toList();
  }

  private String normalizeFeedPreferenceType(String type) {
    String normalized = trimToNull(type);
    if ("event".equals(normalized) || "creator".equals(normalized) || "tag".equals(normalized)) {
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
    if ("friends".equalsIgnoreCase(visibility) || "private".equalsIgnoreCase(visibility)) {
      return "friends";
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

  private String normalizeOriginalFilename(String filename) {
    String normalized = trimToNull(filename);
    if (normalized == null) {
      return null;
    }
    normalized = normalized.replace('\\', '/');
    int lastSlashIndex = normalized.lastIndexOf('/');
    if (lastSlashIndex >= 0) {
      normalized = normalized.substring(lastSlashIndex + 1);
    }
    normalized = normalized.replaceAll("[\\r\\n\\t]", " ").trim();
    if (normalized.isBlank()) {
      return null;
    }
    return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
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

  private record LocalizedInput(String hr, String en) {}
}
