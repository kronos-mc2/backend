package hr.kronos.backend.events.persistence;

import java.time.OffsetDateTime;

public class FeedPreferenceRow {
  private String id;
  private String userId;
  private String blockType;
  private String targetId;
  private String targetLabel;
  private OffsetDateTime createdAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getBlockType() {
    return blockType;
  }

  public void setBlockType(String blockType) {
    this.blockType = blockType;
  }

  public String getTargetId() {
    return targetId;
  }

  public void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  public String getTargetLabel() {
    return targetLabel;
  }

  public void setTargetLabel(String targetLabel) {
    this.targetLabel = targetLabel;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
