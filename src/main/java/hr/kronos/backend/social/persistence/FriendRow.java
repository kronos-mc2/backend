package hr.kronos.backend.social.persistence;

public class FriendRow {
  private String id;
  private String name;
  private String avatarUrl;
  private String statusHr;
  private String statusEn;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getStatusHr() {
    return statusHr;
  }

  public void setStatusHr(String statusHr) {
    this.statusHr = statusHr;
  }

  public String getStatusEn() {
    return statusEn;
  }

  public void setStatusEn(String statusEn) {
    this.statusEn = statusEn;
  }
}
