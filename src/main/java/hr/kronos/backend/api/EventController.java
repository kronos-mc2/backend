package hr.kronos.backend.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CreateFeedPreferenceRequest;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.EventMediaRequest;
import hr.kronos.backend.api.dto.EventParticipantDto;
import hr.kronos.backend.api.dto.EventRatingRequest;
import hr.kronos.backend.api.dto.FeedPreferenceDto;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import hr.kronos.backend.api.dto.UpdateEventRequest;
import hr.kronos.backend.events.EventService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getEvents(from, to, lat, lng, radiusKm, query, userId);
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
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.getFeed(userId, cursor, limit);
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
      Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.createEventWithImages(parseCreateEventRequest(eventJson), images, userId);
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
      @PathVariable String id, @RequestPart("image") MultipartFile image, Authentication authentication) {
    String userId = AuthenticatedUser.userId(authentication);
    return eventService.uploadMedia(id, image, userId);
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
}
