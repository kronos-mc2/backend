package hr.kronos.backend.events;

import hr.kronos.backend.api.dto.AppEventDto;
import hr.kronos.backend.api.dto.EventMediaRequest;
import hr.kronos.backend.storage.StoredObjectContent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EventMediaService {
  private final EventDomainService events;

  public EventMediaService(EventDomainService events) {
    this.events = events;
  }

  @Transactional
  public AppEventDto addMedia(String eventId, EventMediaRequest request, String userId) {
    return events.addMedia(eventId, request, userId);
  }

  @Transactional(readOnly = true)
  public StoredObjectContent getMediaContent(String eventId, String mediaId, String userId) {
    return events.getMediaContent(eventId, mediaId, userId);
  }

  @Transactional(readOnly = true)
  public StoredObjectContent getMediaThumbnailContent(String eventId, String mediaId, String userId) {
    return events.getMediaThumbnailContent(eventId, mediaId, userId);
  }

  @Transactional
  public AppEventDto uploadMedia(String eventId, MultipartFile image, MultipartFile video, String userId) {
    return events.uploadMedia(eventId, image, video, userId);
  }

  @Transactional
  public AppEventDto uploadVideoBytes(String eventId, byte[] bytes, String contentType, String filename, String userId) {
    return events.uploadVideoBytes(eventId, bytes, contentType, filename, userId);
  }

  @Transactional
  public AppEventDto deleteMedia(String eventId, String mediaId, String userId) {
    return events.deleteMedia(eventId, mediaId, userId);
  }
}
