package hr.kronos.backend.profile.persistence;

import java.time.OffsetDateTime;

public class NotificationRow {
  private String id;
  private String notificationType;
  private String title;
  private String body;
  private String eventId;
  private OffsetDateTime createdAt;
  private OffsetDateTime readAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getNotificationType() {
    return notificationType;
  }

  public void setNotificationType(String notificationType) {
    this.notificationType = notificationType;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getReadAt() {
    return readAt;
  }

  public void setReadAt(OffsetDateTime readAt) {
    this.readAt = readAt;
  }
}
