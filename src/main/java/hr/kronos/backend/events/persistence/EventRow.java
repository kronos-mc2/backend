package hr.kronos.backend.events.persistence;

import java.time.OffsetDateTime;

public class EventRow {
  private String id;
  private String titleHr;
  private String titleEn;
  private String whereHr;
  private String whereEn;
  private String aboutHr;
  private String aboutEn;
  private OffsetDateTime whenIso;
  private String eventType;
  private double latitude;
  private double longitude;
  private Double entranceLatitude;
  private Double entranceLongitude;
  private String entryInstructionsHr;
  private String entryInstructionsEn;
  private String visibility;
  private int participantCount;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public int getParticipantCount() {
    return participantCount;
  }

  public void setParticipantCount(int participantCount) {
    this.participantCount = participantCount;
  }
}
