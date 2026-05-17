package hr.kronos.backend.social.persistence;

import java.time.OffsetDateTime;

public class FriendRequestRow {
  private String id;
  private String requesterUserId;
  private String requesterName;
  private String recipientUserId;
  private String recipientName;
  private String chatRoomId;
  private String status;
  private OffsetDateTime createdAt;
  private OffsetDateTime respondedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRequesterUserId() {
    return requesterUserId;
  }

  public void setRequesterUserId(String requesterUserId) {
    this.requesterUserId = requesterUserId;
  }

  public String getRequesterName() {
    return requesterName;
  }

  public void setRequesterName(String requesterName) {
    this.requesterName = requesterName;
  }

  public String getRecipientUserId() {
    return recipientUserId;
  }

  public void setRecipientUserId(String recipientUserId) {
    this.recipientUserId = recipientUserId;
  }

  public String getRecipientName() {
    return recipientName;
  }

  public void setRecipientName(String recipientName) {
    this.recipientName = recipientName;
  }

  public String getChatRoomId() {
    return chatRoomId;
  }

  public void setChatRoomId(String chatRoomId) {
    this.chatRoomId = chatRoomId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getRespondedAt() {
    return respondedAt;
  }

  public void setRespondedAt(OffsetDateTime respondedAt) {
    this.respondedAt = respondedAt;
  }
}
