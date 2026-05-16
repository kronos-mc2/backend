package hr.kronos.backend.notifications.persistence;

public class NotificationPreferencesRow {
  private Boolean directMessagesEnabled;
  private Boolean groupMessagesEnabled;

  public Boolean getDirectMessagesEnabled() {
    return directMessagesEnabled;
  }

  public void setDirectMessagesEnabled(Boolean directMessagesEnabled) {
    this.directMessagesEnabled = directMessagesEnabled;
  }

  public Boolean getGroupMessagesEnabled() {
    return groupMessagesEnabled;
  }

  public void setGroupMessagesEnabled(Boolean groupMessagesEnabled) {
    this.groupMessagesEnabled = groupMessagesEnabled;
  }
}
