package hr.kronos.backend.api;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.FeedPageDto;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import hr.kronos.backend.auth.AuthPrincipal;
import hr.kronos.backend.events.EventService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

  @PostMapping("/events")
  @ResponseStatus(HttpStatus.CREATED)
  public AppEventDto createEvent(@RequestBody CreateEventRequest request, Authentication authentication) {
    AuthPrincipal principal = (AuthPrincipal) authentication.getPrincipal();
    return eventService.createEvent(request, principal.userId());
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
}
