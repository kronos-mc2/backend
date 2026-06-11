package hr.kronos.backend.events;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class EventMediaServiceTest {
  @Mock
  private EventDomainService events;

  @InjectMocks
  private EventMediaService service;

  @Test
  void uploadMediaDelegatesToDomainStorageFlow() {
    MockMultipartFile image = new MockMultipartFile("image", "event.jpg", "image/jpeg", new byte[] {1, 2, 3});

    service.uploadMedia("evt_1", image, null, "usr_owner");

    verify(events).uploadMedia("evt_1", image, null, "usr_owner");
  }
}
