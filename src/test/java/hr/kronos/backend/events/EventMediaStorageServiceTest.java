package hr.kronos.backend.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventMediaRow;
import hr.kronos.backend.storage.ObjectStorageProperties;
import hr.kronos.backend.storage.ObjectStorageService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class EventMediaStorageServiceTest {
  private EventMapper eventMapper;
  private EventMediaStorageService service;

  @BeforeEach
  void setUp() {
    eventMapper = mock(EventMapper.class);
    service = new EventMediaStorageService(eventMapper, mock(ObjectStorageService.class), new ObjectStorageProperties());
  }

  @Test
  void validateUploadedImageCountRejectsMoreThanFiveImages() {
    List<MultipartFile> images = List.of(
        image("1.jpg"),
        image("2.jpg"),
        image("3.jpg"),
        image("4.jpg"),
        image("5.jpg"),
        image("6.jpg"));

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> service.validateUploadedImageCount(images, false));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  @Test
  void ensureMediaCapacityRejectsOverflowingImageLimit() {
    when(eventMapper.findMediaByEventId("evt_1")).thenReturn(List.of(
        media("image"),
        media("image"),
        media("image"),
        media("image"),
        media("image")));

    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> service.ensureMediaCapacity("evt_1", "image", 1));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  @Test
  void mediaContentFilenameSanitizesAndPreservesVideoExtension() {
    EventMediaRow media = media("video");
    media.setOriginalFilename("../raw clip 01");
    media.setContentType("video/mp4");

    assertEquals("raw_clip_01.mp4", service.mediaContentFilename(media));
  }

  private MockMultipartFile image(String filename) {
    return new MockMultipartFile("images", filename, "image/jpeg", new byte[] {1});
  }

  private EventMediaRow media(String type) {
    EventMediaRow media = new EventMediaRow();
    media.setMediaType(type);
    return media;
  }
}
