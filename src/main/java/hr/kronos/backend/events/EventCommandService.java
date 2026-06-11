package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.CreateEventRequest;
import hr.kronos.backend.api.dto.UpdateEventRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EventCommandService {
  private final EventDomainService events;

  public EventCommandService(EventDomainService events) {
    this.events = events;
  }

  @Transactional
  public AppEventDto createEvent(CreateEventRequest request, String creatorUserId) {
    return events.createEvent(request, creatorUserId);
  }

  @Transactional
  public AppEventDto createEventWithImages(
      CreateEventRequest request,
      List<MultipartFile> images,
      MultipartFile video,
      String creatorUserId) {
    return events.createEventWithImages(request, images, video, creatorUserId);
  }

  @Transactional
  public AppEventDto updateEvent(String eventId, UpdateEventRequest request, String userId) {
    return events.updateEvent(eventId, request, userId);
  }

  @Transactional
  public void deleteEvent(String eventId, String userId) {
    events.deleteEvent(eventId, userId);
  }
}
