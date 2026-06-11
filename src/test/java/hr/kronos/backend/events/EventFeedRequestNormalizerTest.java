package hr.kronos.backend.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hr.kronos.backend.events.persistence.EventRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class EventFeedRequestNormalizerTest {
  private final EventFeedRequestNormalizer normalizer = new EventFeedRequestNormalizer();

  @Test
  void normalizeFeedLimitRejectsOutOfRangeLimit() {
    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> normalizer.normalizeFeedLimit(11));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  @Test
  void cursorRoundTripPreservesExcludedEventIds() {
    String cursor = normalizer.encodeFeedCursor(List.of("evt_1", "evt_2"));

    EventFeedRequestNormalizer.FeedCursor parsed = normalizer.parseFeedCursor(cursor);

    assertEquals(0, parsed.offset());
    assertEquals(List.of("evt_1", "evt_2"), parsed.excludeEventIds());
  }

  @Test
  void appendReturnedEventIdsDeduplicatesExistingIds() {
    EventRow returned = new EventRow();
    returned.setId("evt_2");

    List<String> ids = normalizer.appendReturnedEventIds(List.of("evt_1", "evt_2"), List.of(returned));

    assertEquals(List.of("evt_1", "evt_2"), ids);
  }

  @Test
  void normalizeFeedFiltersRejectsSingleCoordinate() {
    ResponseStatusException exception = assertThrows(
        ResponseStatusException.class,
        () -> normalizer.normalizeFeedFilters(null, null, null, 45.0, null, null, null, null, null, null, null));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    assertTrue(exception.getReason().contains("lat and lng"));
  }
}
