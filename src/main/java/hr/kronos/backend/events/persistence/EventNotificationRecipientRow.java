package hr.kronos.backend.events.persistence;

import java.time.OffsetDateTime;

public class EventNotificationRecipientRow {
  private String eventId;
  private String userId;
  private String titleHr;
  private String titleEn;
  private OffsetDateTime startAt;

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getTitleHr() {
    return titleHr;
  }

  public void setTitleHr(String titleHr) {
    this.titleHr = titleHr;
  }

  public String getTitleEn() {
    return titleEn;
  }

  public void setTitleEn(String titleEn) {
    this.titleEn = titleEn;
  }

  public OffsetDateTime getStartAt() {
    return startAt;
  }

  public void setStartAt(OffsetDateTime startAt) {
    this.startAt = startAt;
  }
}
