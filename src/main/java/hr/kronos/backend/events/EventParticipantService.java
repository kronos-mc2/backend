package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.EventParticipantDto;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventParticipantService {
  private final EventDomainService events;

  public EventParticipantService(EventDomainService events) {
    this.events = events;
  }

  @Transactional
  public AppEventDto joinEvent(String eventId, String userId) {
    return events.joinEvent(eventId, userId);
  }

  @Transactional
  public AppEventDto joinEventAfterPayment(String eventId, String userId) {
    return events.joinEventAfterPayment(eventId, userId);
  }

  @Transactional
  public AppEventDto leaveEvent(String eventId, String userId) {
    return events.leaveEvent(eventId, userId);
  }

  @Transactional(readOnly = true)
  public List<EventParticipantDto> getParticipants(String eventId, String userId) {
    return events.getParticipants(eventId, userId);
  }

  @Transactional
  public List<EventParticipantDto> approveParticipant(String eventId, String participantUserId, String userId) {
    return events.approveParticipant(eventId, participantUserId, userId);
  }

  @Transactional
  public List<EventParticipantDto> removeParticipant(String eventId, String participantUserId, String userId) {
    return events.removeParticipant(eventId, participantUserId, userId);
  }

  @Transactional
  public List<EventParticipantDto> blockParticipant(String eventId, String participantUserId, String userId) {
    return events.blockParticipant(eventId, participantUserId, userId);
  }
}
