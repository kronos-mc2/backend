package hr.kronos.backend.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import hr.kronos.backend.events.persistence.EventMapper;
import hr.kronos.backend.events.persistence.EventRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {
  @Mock
  private EventMapper eventMapper;

  @InjectMocks
  private EventService eventService;

  @Test
  void getEventByIdRejectsInaccessiblePrivateEvent() {
    when(eventMapper.findAccessibleById("evt_private", "usr_viewer")).thenReturn(null);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> eventService.getEventById("evt_private", "usr_viewer"));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
  }

  @Test
  void joinEventRejectsInaccessiblePrivateEvent() {
    when(eventMapper.findAccessibleById("evt_private", "usr_viewer")).thenReturn(null);

    ResponseStatusException exception =
        assertThrows(ResponseStatusException.class, () -> eventService.joinEvent("evt_private", "usr_viewer"));

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    verify(eventMapper, never()).upsertParticipant("evt_private", "usr_viewer", "joined");
  }

  @Test
  void getMyEventsUsesJoinedFilter() {
    EventRow row = buildRow("evt_joined", "joined");
    when(eventMapper.findByUser("usr_viewer", "joined")).thenReturn(List.of(row));

    var events = eventService.getMyEvents("usr_viewer", "joined");

    assertEquals(1, events.size());
    assertEquals("evt_joined", events.getFirst().id());
    assertEquals(Boolean.TRUE, events.getFirst().joinedByMe());
  }

  private EventRow buildRow(String id, String participantStatus) {
    EventRow row = new EventRow();
    row.setId(id);
    row.setCreatorUserId("usr_creator");
    row.setTitleHr("Naslov");
    row.setTitleEn("Title");
    row.setWhereHr("Zagreb");
    row.setWhereEn("Zagreb");
    row.setAddress("Ilica 1");
    row.setAboutHr("Opis");
    row.setAboutEn("About");
    row.setWhenIso(OffsetDateTime.parse("2026-04-24T18:00:00Z"));
    row.setStartAt(OffsetDateTime.parse("2026-04-24T18:00:00Z"));
    row.setEndAt(OffsetDateTime.parse("2026-04-24T20:00:00Z"));
    row.setEventType("joined");
    row.setLatitude(45.815);
    row.setLongitude(15.9819);
    row.setVisibility("public");
    row.setAttendanceMode("open");
    row.setStatus("published");
    row.setOrganizerRatingAverage(BigDecimal.ZERO);
    row.setOrganizerRatingCount(0);
    row.setParticipantCount(12);
    row.setUserParticipantStatus(participantStatus);
    return row;
  }
}
