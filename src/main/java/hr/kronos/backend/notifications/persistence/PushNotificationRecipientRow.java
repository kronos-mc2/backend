package hr.kronos.backend.notifications.persistence;

public class PushNotificationRecipientRow {
  private String userId;
  private String tokenId;
  private String token;
  private String roomType;
  private String roomTitle;
  private String locale;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getTokenId() {
    return tokenId;
  }

  public void setTokenId(String tokenId) {
    this.tokenId = tokenId;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getRoomType() {
    return roomType;
  }

  public void setRoomType(String roomType) {
    this.roomType = roomType;
  }

  public String getRoomTitle() {
    return roomTitle;
  }

  public void setRoomTitle(String roomTitle) {
    this.roomTitle = roomTitle;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }
}
