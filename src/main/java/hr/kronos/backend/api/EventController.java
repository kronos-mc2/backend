package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.EventMediaRequest;
import hr.kronos.backend.api.dto.EventParticipantDto;
import hr.kronos.backend.api.dto.EventRatingRequest;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import hr.kronos.backend.api.dto.UpdateEventRequest;
import hr.kronos.backend.auth.AuthPrincipal;
import hr.kronos.backend.events.EventService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class EventController {
  private final EventService eventService;

  public EventController(EventService eventService) {
    this.eventService = eventService;
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
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.getEvents(from, to, lat, lng, radiusKm, query, principal.userId());
  }

  @GetMapping("/events/{id}")
  public AppEventDto getEventById(@PathVariable String id, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.getEventById(id, principal.userId());
  }

  @GetMapping("/feed")
  public FeedPageDto getFeed(
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer limit,
      Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.getFeed(principal.userId(), cursor, limit);
  }

  @GetMapping("/users/me/events")
  public List<AppEventDto> getMyEvents(@RequestParam(required = false) String filter, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.getMyEvents(principal.userId(), filter);
  }

  @GetMapping("/users/me/liked-events")
  public List<AppEventDto> getLikedEvents(Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.getLikedEvents(principal.userId());
  }

  @GetMapping("/users/{userId}/events/upcoming")
  public List<AppEventDto> getUserUpcomingEvents(@PathVariable String userId, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.getUpcomingCreatedByUser(userId, principal.userId());
  }

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.CREATED)
  public AppEventDto createEvent(@RequestBody CreateEventRequest request, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.createEvent(request, principal.userId());
  }

  @PatchMapping("/events/{id}")
  public AppEventDto updateEvent(
      @PathVariable String id, @RequestBody UpdateEventRequest request, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.updateEvent(id, request, principal.userId());
  }

  @DeleteMapping("/events/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteEvent(@PathVariable String id, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    eventService.deleteEvent(id, principal.userId());
  }

  @GetMapping("/events/{id}/participants")
  public List<EventParticipantDto> getEventParticipants(@PathVariable String id, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.getParticipants(id, principal.userId());
  }

  @PostMapping("/events/{id}/media")
  public AppEventDto addEventMedia(
      @PathVariable String id, @RequestBody EventMediaRequest request, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.addMedia(id, request, principal.userId());
  }

  @DeleteMapping("/events/{id}/media/{mediaId}")
  public AppEventDto deleteEventMedia(
      @PathVariable String id, @PathVariable String mediaId, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.deleteMedia(id, mediaId, principal.userId());
  }

  @PostMapping("/events/{id}/participants/{userId}/approve")
  public List<EventParticipantDto> approveEventParticipant(
      @PathVariable String id, @PathVariable String userId, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.approveParticipant(id, userId, principal.userId());
  }

  @DeleteMapping("/events/{id}/participants/{userId}")
  public List<EventParticipantDto> removeEventParticipant(
      @PathVariable String id, @PathVariable String userId, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.removeParticipant(id, userId, principal.userId());
  }

  @PostMapping("/events/{id}/participants/{userId}/block")
  public List<EventParticipantDto> blockEventParticipant(
      @PathVariable String id, @PathVariable String userId, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.blockParticipant(id, userId, principal.userId());
  }

  @PostMapping("/events/{id}/join")
  public AppEventDto joinEvent(@PathVariable String id, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.joinEvent(id, principal.userId());
  }

  @DeleteMapping("/events/{id}/join")
  public AppEventDto leaveEvent(@PathVariable String id, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.leaveEvent(id, principal.userId());
  }

  @PostMapping("/events/{id}/like")
  public AppEventDto likeEvent(@PathVariable String id, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.likeEvent(id, principal.userId());
  }

  @DeleteMapping("/events/{id}/like")
  public AppEventDto unlikeEvent(@PathVariable String id, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.unlikeEvent(id, principal.userId());
  }

  @PostMapping("/events/{id}/ratings")
  public AppEventDto rateOrganizer(
      @PathVariable String id, @RequestBody OrganizerRatingRequest request, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.rateOrganizer(id, request, principal.userId());
  }

  @PostMapping("/events/{id}/ratings/full")
  public AppEventDto rateEvent(
      @PathVariable String id, @RequestBody EventRatingRequest request, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.rateEvent(id, request, principal.userId());
  }
}
