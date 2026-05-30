package hr.kronos.backend.auth.persistence;

import java.time.OffsetDateTime;

public class UserDeletionRecoveryRow {
  private String userId;
  private String originalEmail;
  private String originalFullName;
  private String originalBio;
  private String originalAvatarUrl;
  private String originalPasswordHash;
  private String originalAuthProvider;
  private OffsetDateTime requestedAt;
  private OffsetDateTime scheduledAt;

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getOriginalEmail() {
    return originalEmail;
  }

  public void setOriginalEmail(String originalEmail) {
    this.originalEmail = originalEmail;
  }

  public String getOriginalFullName() {
    return originalFullName;
  }

  public void setOriginalFullName(String originalFullName) {
    this.originalFullName = originalFullName;
  }

  public String getOriginalBio() {
    return originalBio;
  }

  public void setOriginalBio(String originalBio) {
    this.originalBio = originalBio;
  }

  public String getOriginalAvatarUrl() {
    return originalAvatarUrl;
  }

  public void setOriginalAvatarUrl(String originalAvatarUrl) {
    this.originalAvatarUrl = originalAvatarUrl;
  }

  public String getOriginalPasswordHash() {
    return originalPasswordHash;
  }

  public void setOriginalPasswordHash(String originalPasswordHash) {
    this.originalPasswordHash = originalPasswordHash;
  }

  public String getOriginalAuthProvider() {
    return originalAuthProvider;
  }

  public void setOriginalAuthProvider(String originalAuthProvider) {
    this.originalAuthProvider = originalAuthProvider;
  }

  public OffsetDateTime getRequestedAt() {
    return requestedAt;
  }

  public void setRequestedAt(OffsetDateTime requestedAt) {
    this.requestedAt = requestedAt;
  }

  public OffsetDateTime getScheduledAt() {
    return scheduledAt;
  }

  public void setScheduledAt(OffsetDateTime scheduledAt) {
    this.scheduledAt = scheduledAt;
  }
}
