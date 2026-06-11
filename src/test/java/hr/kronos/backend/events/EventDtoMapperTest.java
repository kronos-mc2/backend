package hr.kronos.backend.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import hr.kronos.backend.events.persistence.EventMediaRow;
import org.junit.jupiter.api.Test;

class EventDtoMapperTest {
  private final EventDtoMapper mapper = new EventDtoMapper(mock(EventMediaStorageService.class));

  @Test
  void toMediaDtoUsesAuthenticatedThumbnailUrlWhenThumbnailIsStored() {
    EventMediaRow row = new EventMediaRow();
    row.setId("media_1");
    row.setEventId("evt_1");
    row.setMediaType("image");
    row.setStorageKey("events/evt_1/image/a.jpg");
    row.setThumbnailStorageKey("events/evt_1/thumbnail/a.jpg");
    row.setSortOrder(2);

    var dto = mapper.toMediaDto(row);

    assertEquals("/api/events/evt_1/media/media_1/content", dto.url());
    assertEquals("/api/events/evt_1/media/media_1/thumbnail", dto.thumbnailUrl());
    assertEquals(2, dto.sortOrder());
  }
}
