package hr.kronos.backend.messages.persistence;

import java.time.OffsetDateTime;

public class PollRow {
  private String id;
  private String roomId;
  private String question;
  private Boolean allowMultiple;
  private String createdByUserId;
  private OffsetDateTime closesAt;
  private OffsetDateTime createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public Boolean getAllowMultiple() {
    return allowMultiple;
  }

  public void setAllowMultiple(Boolean allowMultiple) {
    this.allowMultiple = allowMultiple;
  }

  public String getCreatedByUserId() {
    return createdByUserId;
  }

  public void setCreatedByUserId(String createdByUserId) {
    this.createdByUserId = createdByUserId;
  }

  public OffsetDateTime getClosesAt() {
    return closesAt;
  }

  public void setClosesAt(OffsetDateTime closesAt) {
    this.closesAt = closesAt;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
