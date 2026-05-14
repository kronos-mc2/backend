package hr.kronos.backend.messages.persistence;

import java.time.OffsetDateTime;

public class ChatRoomRow {
  private String id;
  private String roomType;
  private String title;
  private String eventId;
  private Boolean adminOnly;
  private String createdByUserId;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;
  private String displayTitle;
  private String avatarUrl;
  private String directUserId;
  private String subtitle;
  private String lastMessage;
  private OffsetDateTime lastMessageAt;
  private String timeLabel;
  private Integer unreadCount;
  private Integer memberCount;
  private String myRole;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRoomType() {
    return roomType;
  }

  public void setRoomType(String roomType) {
    this.roomType = roomType;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getEventId() {
    return eventId;
  }

  public void setEventId(String eventId) {
    this.eventId = eventId;
  }

  public Boolean getAdminOnly() {
    return adminOnly;
  }

  public void setAdminOnly(Boolean adminOnly) {
    this.adminOnly = adminOnly;
  }

  public String getCreatedByUserId() {
    return createdByUserId;
  }

  public void setCreatedByUserId(String createdByUserId) {
    this.createdByUserId = createdByUserId;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getDisplayTitle() {
    return displayTitle;
  }

  public void setDisplayTitle(String displayTitle) {
    this.displayTitle = displayTitle;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getDirectUserId() {
    return directUserId;
  }

  public void setDirectUserId(String directUserId) {
    this.directUserId = directUserId;
  }

  public String getSubtitle() {
    return subtitle;
  }

  public void setSubtitle(String subtitle) {
    this.subtitle = subtitle;
  }

  public String getLastMessage() {
    return lastMessage;
  }

  public void setLastMessage(String lastMessage) {
    this.lastMessage = lastMessage;
  }

  public OffsetDateTime getLastMessageAt() {
    return lastMessageAt;
  }

  public void setLastMessageAt(OffsetDateTime lastMessageAt) {
    this.lastMessageAt = lastMessageAt;
  }

  public String getTimeLabel() {
    return timeLabel;
  }

  public void setTimeLabel(String timeLabel) {
    this.timeLabel = timeLabel;
  }

  public Integer getUnreadCount() {
    return unreadCount;
  }

  public void setUnreadCount(Integer unreadCount) {
    this.unreadCount = unreadCount;
  }

  public Integer getMemberCount() {
    return memberCount;
  }

  public void setMemberCount(Integer memberCount) {
    this.memberCount = memberCount;
  }

  public String getMyRole() {
    return myRole;
  }

  public void setMyRole(String myRole) {
    this.myRole = myRole;
  }
}
