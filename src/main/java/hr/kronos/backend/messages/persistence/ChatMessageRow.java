package hr.kronos.backend.messages.persistence;

import java.time.OffsetDateTime;

public class ChatMessageRow {
  private String id;
  private String roomId;
  private String senderUserId;
  private String senderName;
  private String senderAvatarUrl;
  private String messageType;
  private String body;
  private String eventId;
  private String pollId;
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

  public String getSenderUserId() {
    return senderUserId;
  }

  public void setSenderUserId(String senderUserId) {
    this.senderUserId = senderUserId;
  }

  public String getSenderName() {
    return senderName;
  }

  public void setSenderName(String senderName) {
    this.senderName = senderName;
  }

  public String getSenderAvatarUrl() {
    return senderAvatarUrl;
  }

  public void setSenderAvatarUrl(String senderAvatarUrl) {
    this.senderAvatarUrl = senderAvatarUrl;
  }

  public String getMessageType() {
    return messageType;
  }

  public void setMessageType(String messageType) {
    this.messageType = messageType;
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

  public String getPollId() {
    return pollId;
  }

  public void setPollId(String pollId) {
    this.pollId = pollId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
