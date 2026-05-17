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
  private Boolean mutedByMe;
  private String friendshipStatus;
  private String friendRequestId;
  private String friendRequestRequesterUserId;
  private String friendRequestRequesterName;
  private String friendRequestRecipientUserId;
  private String friendRequestRecipientName;
  private String friendRequestStatus;
  private OffsetDateTime friendRequestCreatedAt;
  private OffsetDateTime friendRequestRespondedAt;

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

  public Boolean getMutedByMe() {
    return mutedByMe;
  }

  public void setMutedByMe(Boolean mutedByMe) {
    this.mutedByMe = mutedByMe;
  }

  public String getFriendshipStatus() {
    return friendshipStatus;
  }

  public void setFriendshipStatus(String friendshipStatus) {
    this.friendshipStatus = friendshipStatus;
  }

  public String getFriendRequestId() {
    return friendRequestId;
  }

  public void setFriendRequestId(String friendRequestId) {
    this.friendRequestId = friendRequestId;
  }

  public String getFriendRequestRequesterUserId() {
    return friendRequestRequesterUserId;
  }

  public void setFriendRequestRequesterUserId(String friendRequestRequesterUserId) {
    this.friendRequestRequesterUserId = friendRequestRequesterUserId;
  }

  public String getFriendRequestRequesterName() {
    return friendRequestRequesterName;
  }

  public void setFriendRequestRequesterName(String friendRequestRequesterName) {
    this.friendRequestRequesterName = friendRequestRequesterName;
  }

  public String getFriendRequestRecipientUserId() {
    return friendRequestRecipientUserId;
  }

  public void setFriendRequestRecipientUserId(String friendRequestRecipientUserId) {
    this.friendRequestRecipientUserId = friendRequestRecipientUserId;
  }

  public String getFriendRequestRecipientName() {
    return friendRequestRecipientName;
  }

  public void setFriendRequestRecipientName(String friendRequestRecipientName) {
    this.friendRequestRecipientName = friendRequestRecipientName;
  }

  public String getFriendRequestStatus() {
    return friendRequestStatus;
  }

  public void setFriendRequestStatus(String friendRequestStatus) {
    this.friendRequestStatus = friendRequestStatus;
  }

  public OffsetDateTime getFriendRequestCreatedAt() {
    return friendRequestCreatedAt;
  }

  public void setFriendRequestCreatedAt(OffsetDateTime friendRequestCreatedAt) {
    this.friendRequestCreatedAt = friendRequestCreatedAt;
  }

  public OffsetDateTime getFriendRequestRespondedAt() {
    return friendRequestRespondedAt;
  }

  public void setFriendRequestRespondedAt(OffsetDateTime friendRequestRespondedAt) {
    this.friendRequestRespondedAt = friendRequestRespondedAt;
  }
}
