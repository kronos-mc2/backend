package hr.kronos.backend.events.persistence;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class EventRow {
  private String id;
  private String creatorUserId;
  private String creatorName;
  private String creatorAvatarUrl;
  private String titleHr;
  private String titleEn;
  private String whereHr;
  private String whereEn;
  private String address;
  private String aboutHr;
  private String aboutEn;
  private OffsetDateTime whenIso;
  private OffsetDateTime startAt;
  private OffsetDateTime endAt;
  private String eventType;
  private double latitude;
  private double longitude;
  private Double entranceLatitude;
  private Double entranceLongitude;
  private String entryInstructionsHr;
  private String entryInstructionsEn;
  private String visibility;
  private String attendanceMode;
  private BigDecimal priceAmount;
  private String priceCurrency;
  private Integer capacity;
  private String status;
  private BigDecimal eventRatingAverage;
  private int eventRatingCount;
  private BigDecimal organizerRatingAverage;
  private int organizerRatingCount;
  private int likeCount;
  private Boolean likedByMe;
  private int participantCount;
  private int waitlistCount;
  private String userParticipantStatus;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCreatorUserId() {
    return creatorUserId;
  }

  public void setCreatorUserId(String creatorUserId) {
    this.creatorUserId = creatorUserId;
  }

  public String getCreatorName() {
    return creatorName;
  }

  public void setCreatorName(String creatorName) {
    this.creatorName = creatorName;
  }

  public String getCreatorAvatarUrl() {
    return creatorAvatarUrl;
  }

  public void setCreatorAvatarUrl(String creatorAvatarUrl) {
    this.creatorAvatarUrl = creatorAvatarUrl;
  }

  public String getTitleHr() {
    return titleHr;
  }

  public void setTitleHr(String titleHr) {
    this.titleHr = titleHr;
  }

  public String getTitleEn() {
    return titleEn;
  }

  public void setTitleEn(String titleEn) {
    this.titleEn = titleEn;
  }

  public String getWhereHr() {
    return whereHr;
  }

  public void setWhereHr(String whereHr) {
    this.whereHr = whereHr;
  }

  public String getWhereEn() {
    return whereEn;
  }

  public void setWhereEn(String whereEn) {
    this.whereEn = whereEn;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getAboutHr() {
    return aboutHr;
  }

  public void setAboutHr(String aboutHr) {
    this.aboutHr = aboutHr;
  }

  public String getAboutEn() {
    return aboutEn;
  }

  public void setAboutEn(String aboutEn) {
    this.aboutEn = aboutEn;
  }

  public OffsetDateTime getWhenIso() {
    return whenIso;
  }

  public void setWhenIso(OffsetDateTime whenIso) {
    this.whenIso = whenIso;
  }

  public OffsetDateTime getStartAt() {
    return startAt;
  }

  public void setStartAt(OffsetDateTime startAt) {
    this.startAt = startAt;
  }

  public OffsetDateTime getEndAt() {
    return endAt;
  }

  public void setEndAt(OffsetDateTime endAt) {
    this.endAt = endAt;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public double getLatitude() {
    return latitude;
  }

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public double getLongitude() {
    return longitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
  }

  public Double getEntranceLatitude() {
    return entranceLatitude;
  }

  public void setEntranceLatitude(Double entranceLatitude) {
    this.entranceLatitude = entranceLatitude;
  }

  public Double getEntranceLongitude() {
    return entranceLongitude;
  }

  public void setEntranceLongitude(Double entranceLongitude) {
    this.entranceLongitude = entranceLongitude;
  }

  public String getEntryInstructionsHr() {
    return entryInstructionsHr;
  }

  public void setEntryInstructionsHr(String entryInstructionsHr) {
    this.entryInstructionsHr = entryInstructionsHr;
  }

  public String getEntryInstructionsEn() {
    return entryInstructionsEn;
  }

  public void setEntryInstructionsEn(String entryInstructionsEn) {
    this.entryInstructionsEn = entryInstructionsEn;
  }

  public String getVisibility() {
    return visibility;
  }

  public void setVisibility(String visibility) {
    this.visibility = visibility;
  }

  public String getAttendanceMode() {
    return attendanceMode;
  }

  public void setAttendanceMode(String attendanceMode) {
    this.attendanceMode = attendanceMode;
  }

  public BigDecimal getPriceAmount() {
    return priceAmount;
  }

  public void setPriceAmount(BigDecimal priceAmount) {
    this.priceAmount = priceAmount;
  }

  public String getPriceCurrency() {
    return priceCurrency;
  }

  public void setPriceCurrency(String priceCurrency) {
    this.priceCurrency = priceCurrency;
  }

  public Integer getCapacity() {
    return capacity;
  }

  public void setCapacity(Integer capacity) {
    this.capacity = capacity;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public BigDecimal getEventRatingAverage() {
    return eventRatingAverage;
  }

  public void setEventRatingAverage(BigDecimal eventRatingAverage) {
    this.eventRatingAverage = eventRatingAverage;
  }

  public int getEventRatingCount() {
    return eventRatingCount;
  }

  public void setEventRatingCount(int eventRatingCount) {
    this.eventRatingCount = eventRatingCount;
  }

  public BigDecimal getOrganizerRatingAverage() {
    return organizerRatingAverage;
  }

  public void setOrganizerRatingAverage(BigDecimal organizerRatingAverage) {
    this.organizerRatingAverage = organizerRatingAverage;
  }

  public int getOrganizerRatingCount() {
    return organizerRatingCount;
  }

  public void setOrganizerRatingCount(int organizerRatingCount) {
    this.organizerRatingCount = organizerRatingCount;
  }

  public int getLikeCount() {
    return likeCount;
  }

  public void setLikeCount(int likeCount) {
    this.likeCount = likeCount;
  }

  public Boolean getLikedByMe() {
    return likedByMe;
  }

  public void setLikedByMe(Boolean likedByMe) {
    this.likedByMe = likedByMe;
  }

  public int getParticipantCount() {
    return participantCount;
  }

  public void setParticipantCount(int participantCount) {
    this.participantCount = participantCount;
  }

  public int getWaitlistCount() {
    return waitlistCount;
  }

  public void setWaitlistCount(int waitlistCount) {
    this.waitlistCount = waitlistCount;
  }

  public String getUserParticipantStatus() {
    return userParticipantStatus;
  }

  public void setUserParticipantStatus(String userParticipantStatus) {
    this.userParticipantStatus = userParticipantStatus;
  }
}
