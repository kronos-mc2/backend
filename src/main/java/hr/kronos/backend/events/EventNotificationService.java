package hr.kronos.backend.events;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventNotificationService {
  private final EventDomainService events;

  public EventNotificationService(EventDomainService events) {
    this.events = events;
  }

  @Transactional
  @Scheduled(fixedDelayString = "PT1H")
  public void markPastEventsFinished() {
    events.markPastEventsFinished();
  }

  @Transactional
  @Scheduled(fixedDelayString = "PT1H")
  public void sendEventReminders() {
    events.sendEventReminders();
  }
}
