package hr.kronos.backend.messages.persistence;

public class ConversationRow {
  private String id;
  private String contact;
  private String lastMessageHr;
  private String lastMessageEn;
  private String timeLabel;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getContact() {
    return contact;
  }

  public void setContact(String contact) {
    this.contact = contact;
  }

  public String getLastMessageHr() {
    return lastMessageHr;
  }

  public void setLastMessageHr(String lastMessageHr) {
    this.lastMessageHr = lastMessageHr;
  }

  public String getLastMessageEn() {
    return lastMessageEn;
  }

  public void setLastMessageEn(String lastMessageEn) {
    this.lastMessageEn = lastMessageEn;
  }

  public String getTimeLabel() {
    return timeLabel;
  }

  public void setTimeLabel(String timeLabel) {
    this.timeLabel = timeLabel;
  }
}
