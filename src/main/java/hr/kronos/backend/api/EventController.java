package hr.kronos.backend.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CreateFeedPreferenceRequest;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.EventMediaRequest;
import hr.kronos.backend.api.dto.EventParticipantDto;
import hr.kronos.backend.api.dto.EventRatingRequest;
import hr.kronos.backend.api.dto.FeedImpressionRequest;
import hr.kronos.backend.api.dto.FeedPreferenceDto;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import hr.kronos.backend.api.dto.SavedEventsOverviewDto;
import hr.kronos.backend.api.dto.UpdateEventRequest;
import hr.kronos.backend.events.EventService;
import hr.kronos.backend.storage.StoredObjectContent;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class EventController {
  private final EventService eventService;
  private final ObjectMapper objectMapper;

  public EventController(EventService eventService, ObjectMapper objectMapper) {
    this.eventService = eventService;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/events")
  public List<AppEventDto> getEvents(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) Double lat,
      @RequestParam(required = false) Double lng,
      @RequestParam(required = false) Double radiusKm,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String tags,
      @RequestParam(required = false) String attendanceModes,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getEvents(from, to, lat, lng, radiusKm, query, tags, attendanceModes, userId);
  }

  @GetMapping("/events/{id}")
  public AppEventDto getEventById(@PathVariable String id, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getEventById(id, userId);
  }

  @GetMapping("/feed")
  public FeedPageDto getFeed(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String seed,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getFeed(userId, cursor, limit, seed);
  }

  @PostMapping("/feed/impressions")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void recordFeedImpression(@RequestBody FeedImpressionRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    eventService.recordFeedImpression(request, userId);
  }

  @GetMapping("/users/me/events")
  public List<AppEventDto> getMyEvents(@RequestParam(required = false) String filter, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getMyEvents(userId, filter);
  }

  @GetMapping("/users/me/liked-events")
  public List<AppEventDto> getLikedEvents(Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getLikedEvents(userId);
  }

  @GetMapping("/users/me/saved-events/overview")
  public SavedEventsOverviewDto getSavedEventsOverview(Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getSavedEventsOverview(userId);
  }

  @GetMapping("/users/me/feed-preferences")
  public List<FeedPreferenceDto> getFeedPreferences(Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getFeedPreferences(userId);
  }

  @PostMapping("/users/me/feed-preferences")
  @ResponseStatus(HttpStatus.CREATED)
  public FeedPreferenceDto createFeedPreference(
      @RequestBody CreateFeedPreferenceRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.createFeedPreference(request, userId);
  }

  @DeleteMapping("/users/me/feed-preferences/{preferenceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteFeedPreference(@PathVariable String preferenceId, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    eventService.deleteFeedPreference(preferenceId, userId);
  }

  @GetMapping("/users/{userId}/events/upcoming")
  public List<AppEventDto> getUserUpcomingEvents(@PathVariable String userId, Authentication authentication) {
    String requesterUserId = AuthenticatedUser.userId(authentication);
    return eventService.getUpcomingCreatedByUser(userId, requesterUserId);
  }

  @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AppEventDto createEvent(@RequestBody CreateEventRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.createEvent(request, userId);
  }

  @PostMapping(value = "/events", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public AppEventDto createEventWithImages(
      @RequestPart("event") String eventJson,
      @RequestPart("images") List<MultipartFile> images,
      @RequestPart(value = "video", required = false) MultipartFile video,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.createEventWithImages(parseCreateEventRequest(eventJson), images, video, userId);
  }

  @PatchMapping("/events/{id}")
  public AppEventDto updateEvent(
      @PathVariable String id, @RequestBody UpdateEventRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.updateEvent(id, request, userId);
  }

  @DeleteMapping("/events/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteEvent(@PathVariable String id, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    eventService.deleteEvent(id, userId);
  }

  @GetMapping("/events/{id}/participants")
  public List<EventParticipantDto> getEventParticipants(@PathVariable String id, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getParticipants(id, userId);
  }

  @PostMapping(value = "/events/{id}/media", consumes = MediaType.APPLICATION_JSON_VALUE)
  public AppEventDto addEventMedia(
      @PathVariable String id, @RequestBody EventMediaRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.addMedia(id, request, userId);
  }

  @PostMapping(value = "/events/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AppEventDto uploadEventMedia(
      @PathVariable String id,
      @RequestPart(value = "image", required = false) MultipartFile image,
      @RequestPart(value = "video", required = false) MultipartFile video,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.uploadMedia(id, image, video, userId);
  }

  @PostMapping(
      value = "/events/{id}/media/video",
      consumes = {MediaType.APPLICATION_OCTET_STREAM_VALUE, "video/mp4", "video/quicktime", "video/x-m4v"})
  public AppEventDto uploadEventVideoBytes(
      @PathVariable String id,
      @RequestBody byte[] bytes,
      @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
      @RequestHeader(value = "X-File-Name", required = false) String filename,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.uploadVideoBytes(id, bytes, contentType, decodeHeaderValue(filename), userId);
  }

  @GetMapping("/events/{id}/media/{mediaId}/content")
  public ResponseEntity<byte[]> getEventMediaContent(
      @PathVariable String id,
      @PathVariable String mediaId,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    StoredObjectContent content = eventService.getMediaContent(id, mediaId, userId);
    ByteRange byteRange = parseByteRange(rangeHeader, content.contentLength());
    if (byteRange != null) {
      byte[] body = slice(content.bytes(), byteRange.start(), byteRange.end());
      return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
          .contentType(MediaType.parseMediaType(content.contentType()))
          .contentLength(body.length)
          .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
          .header("X-Content-Type-Options", "nosniff")
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .header(HttpHeaders.CONTENT_RANGE, "bytes " + byteRange.start() + "-" + byteRange.end() + "/" + content.contentLength())
          .body(body);
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(content.contentType()))
        .contentLength(content.contentLength())
        .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .body(content.bytes());
  }

  @GetMapping("/events/{id}/media/{mediaId}/content/{filename}")
  public ResponseEntity<byte[]> getEventMediaContentWithFilename(
      @PathVariable String id,
      @PathVariable String mediaId,
      @PathVariable String filename,
      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
      Authentication authentication) {
    return getEventMediaContent(id, mediaId, rangeHeader, authentication);
  }

  @DeleteMapping("/events/{id}/media/{mediaId}")
  public AppEventDto deleteEventMedia(
      @PathVariable String id, @PathVariable String mediaId, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.deleteMedia(id, mediaId, userId);
  }

  @PostMapping("/events/{id}/participants/{userId}/approve")
  public List<EventParticipantDto> approveEventParticipant(
      @PathVariable String id, @PathVariable String userId, Authentication authentication) {
    String requesterUserId = AuthenticatedUser.userId(authentication);
    return eventService.approveParticipant(id, userId, requesterUserId);
  }

  @DeleteMapping("/events/{id}/participants/{userId}")
  public List<EventParticipantDto> removeEventParticipant(
      @PathVariable String id, @PathVariable String userId, Authentication authentication) {
    String requesterUserId = AuthenticatedUser.userId(authentication);
    return eventService.removeParticipant(id, userId, requesterUserId);
  }

  @PostMapping("/events/{id}/participants/{userId}/block")
  public List<EventParticipantDto> blockEventParticipant(
      @PathVariable String id, @PathVariable String userId, Authentication authentication) {
    String requesterUserId = AuthenticatedUser.userId(authentication);
    return eventService.blockParticipant(id, userId, requesterUserId);
  }

  @PostMapping("/events/{id}/join")
  public AppEventDto joinEvent(@PathVariable String id, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.joinEvent(id, userId);
  }

  @DeleteMapping("/events/{id}/join")
  public AppEventDto leaveEvent(@PathVariable String id, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.leaveEvent(id, userId);
  }

  @PostMapping("/events/{id}/like")
  public AppEventDto likeEvent(@PathVariable String id, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.likeEvent(id, userId);
  }

  @DeleteMapping("/events/{id}/like")
  public AppEventDto unlikeEvent(@PathVariable String id, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.unlikeEvent(id, userId);
  }

  @PostMapping("/events/{id}/ratings")
  public AppEventDto rateOrganizer(
      @PathVariable String id, @RequestBody OrganizerRatingRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.rateOrganizer(id, request, userId);
  }

  @PostMapping("/events/{id}/ratings/full")
  public AppEventDto rateEvent(
      @PathVariable String id, @RequestBody EventRatingRequest request, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.rateEvent(id, request, userId);
  }

  private CreateEventRequest parseCreateEventRequest(String eventJson) {
    try {
      return objectMapper.readValue(eventJson, CreateEventRequest.class);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "event payload is invalid.", exception);
    }
  }

  private String decodeHeaderValue(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    return URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private static ByteRange parseByteRange(String rangeHeader, long contentLength) {
    if (rangeHeader == null || !rangeHeader.startsWith("bytes=") || contentLength <= 0) {
      return null;
    }
    String range = rangeHeader.substring("bytes=".length()).trim();
    int separatorIndex = range.indexOf('-');
    if (separatorIndex < 0 || range.indexOf(',') >= 0) {
      return null;
    }
    try {
      String startText = range.substring(0, separatorIndex).trim();
      String endText = range.substring(separatorIndex + 1).trim();
      if (startText.isEmpty()) {
        long suffixLength = Long.parseLong(endText);
        if (suffixLength <= 0) {
          return null;
        }
        long start = Math.max(0, contentLength - suffixLength);
        return new ByteRange(start, contentLength - 1);
      }
      long start = Long.parseLong(startText);
      long end = endText.isEmpty() ? contentLength - 1 : Long.parseLong(endText);
      if (start < 0 || end < start || start >= contentLength) {
        return null;
      }
      return new ByteRange(start, Math.min(end, contentLength - 1));
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static byte[] slice(byte[] bytes, long start, long end) {
    int from = Math.toIntExact(start);
    int toExclusive = Math.toIntExact(end + 1);
    return java.util.Arrays.copyOfRange(bytes, from, toExclusive);
  }

  private record ByteRange(long start, long end) {}
}
