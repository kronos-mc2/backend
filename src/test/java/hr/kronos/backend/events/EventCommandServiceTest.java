package hr.kronos.backend.events;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import hr.kronos.backend.api.dto.CreateEventRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventCommandServiceTest {
  @Mock
  private EventDomainService events;

  @InjectMocks
  private EventCommandService service;

  @Test
  void createEventDelegatesToDomainBoundary() {
    CreateEventRequest request = mock(CreateEventRequest.class);

    service.createEvent(request, "usr_creator");

    verify(events).createEvent(request, "usr_creator");
  }
}
