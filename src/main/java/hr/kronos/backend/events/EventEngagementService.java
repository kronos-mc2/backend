package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.EventRatingRequest;
import hr.kronos.backend.api.dto.OrganizerRatingRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventEngagementService {
  private final EventDomainService events;

  public EventEngagementService(EventDomainService events) {
    this.events = events;
  }

  @Transactional
  public AppEventDto likeEvent(String eventId, String userId) {
    return events.likeEvent(eventId, userId);
  }

  @Transactional
  public AppEventDto unlikeEvent(String eventId, String userId) {
    return events.unlikeEvent(eventId, userId);
  }

  @Transactional
  public AppEventDto rateOrganizer(String eventId, OrganizerRatingRequest request, String userId) {
    return events.rateOrganizer(eventId, request, userId);
  }

  @Transactional
  public AppEventDto rateEvent(String eventId, EventRatingRequest request, String userId) {
    return events.rateEvent(eventId, request, userId);
  }
}
