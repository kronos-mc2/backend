package hr.kronos.backend.events;

import hr.kronos.backend.events.persistence.EventRow;
import java.time.OffsetDateTime;

final class EventRules {
  private static final String STATUS_PUBLISHED = "published";
  private static final String PARTICIPANT_STATUS_APPROVED = "approved";
  private static final String PARTICIPANT_STATUS_JOINED = "joined";
  private static final String PARTICIPANT_STATUS_REJECTED = "rejected";
  private static final String PARTICIPANT_STATUS_WAITLISTED = "waitlisted";

  private EventRules() {}

  static boolean canJoin(EventRow row) {
    if (!STATUS_PUBLISHED.equals(row.getStatus())) {
      return false;
    }
    if (isEventEnded(row)) {
      return false;
    }

    Integer capacity = row.getCapacity();
    if (isJoinedByMe(row.getUserParticipantStatus())) {
      return true;
    }

    if ("blocked".equals(row.getUserParticipantStatus()) || PARTICIPANT_STATUS_REJECTED.equals(row.getUserParticipantStatus())) {
      return false;
    }

    return capacity == null || row.getParticipantCount() < capacity;
  }

  static boolean isJoinedByMe(String status) {
    return PARTICIPANT_STATUS_JOINED.equals(status)
        || PARTICIPANT_STATUS_APPROVED.equals(status)
        || PARTICIPANT_STATUS_WAITLISTED.equals(status);
  }

  static boolean isEventEnded(EventRow row) {
    OffsetDateTime endAt = row.getEndAt();
    OffsetDateTime startAt = row.getStartAt() == null ? row.getWhenIso() : row.getStartAt();
    OffsetDateTime relevantEnd = endAt == null ? startAt : endAt;
    return relevantEnd != null && relevantEnd.isBefore(OffsetDateTime.now());
  }

  static boolean canRateOrganizer(EventRow row, String userId) {
    if (row.getCreatorUserId() == null || row.getCreatorUserId().equals(userId)) {
      return false;
    }
    if (!PARTICIPANT_STATUS_JOINED.equals(row.getUserParticipantStatus())
        && !PARTICIPANT_STATUS_APPROVED.equals(row.getUserParticipantStatus())) {
      return false;
    }
    if ("finished".equals(row.getStatus())) {
      return true;
    }
    return isEventEnded(row);
  }
}
