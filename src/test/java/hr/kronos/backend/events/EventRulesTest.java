package hr.kronos.backend.events;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hr.kronos.backend.events.persistence.EventRow;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class EventRulesTest {
  @Test
  void canJoinAllowsAlreadyJoinedFullEvent() {
    EventRow row = joinableEvent();
    row.setCapacity(1);
    row.setParticipantCount(1);
    row.setUserParticipantStatus("joined");

    assertTrue(EventRules.canJoin(row));
  }

  @Test
  void canJoinRejectsRejectedUserEvenWhenCapacityAvailable() {
    EventRow row = joinableEvent();
    row.setCapacity(10);
    row.setParticipantCount(1);
    row.setUserParticipantStatus("rejected");

    assertFalse(EventRules.canJoin(row));
  }

  @Test
  void canRateOrganizerRejectsWaitlistedUser() {
    EventRow row = joinableEvent();
    row.setCreatorUserId("usr_owner");
    row.setUserParticipantStatus("waitlisted");
    row.setEndAt(OffsetDateTime.now().minusDays(1));

    assertFalse(EventRules.canRateOrganizer(row, "usr_waitlisted"));
  }

  private EventRow joinableEvent() {
    EventRow row = new EventRow();
    row.setStatus("published");
    row.setStartAt(OffsetDateTime.now().plusDays(1));
    row.setEndAt(OffsetDateTime.now().plusDays(1).plusHours(2));
    return row;
  }
}
