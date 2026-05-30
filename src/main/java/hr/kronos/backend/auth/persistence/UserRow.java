package hr.kronos.backend.auth.persistence;

import java.time.OffsetDateTime;

public class UserRow {
  private String id;
  private String email;
  private String fullName;
  private String passwordHash;
  private String authProvider;
  private String bio;
  private String avatarUrl;
  private String avatarStorageKey;
  private String avatarBucketName;
  private String avatarContentType;
  private Long avatarByteSize;
  private String accountStatus;
  private OffsetDateTime deletionRequestedAt;
  private OffsetDateTime deletionScheduledAt;
  private OffsetDateTime deletedAt;
  private OffsetDateTime createdAt;
  private OffsetDateTime updatedAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getAuthProvider() {
    return authProvider;
  }

  public void setAuthProvider(String authProvider) {
    this.authProvider = authProvider;
  }

  public String getBio() {
    return bio;
  }

  public void setBio(String bio) {
    this.bio = bio;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getAvatarStorageKey() {
    return avatarStorageKey;
  }

  public void setAvatarStorageKey(String avatarStorageKey) {
    this.avatarStorageKey = avatarStorageKey;
  }

  public String getAvatarBucketName() {
    return avatarBucketName;
  }

  public void setAvatarBucketName(String avatarBucketName) {
    this.avatarBucketName = avatarBucketName;
  }

  public String getAvatarContentType() {
    return avatarContentType;
  }

  public void setAvatarContentType(String avatarContentType) {
    this.avatarContentType = avatarContentType;
  }

  public Long getAvatarByteSize() {
    return avatarByteSize;
  }

  public void setAvatarByteSize(Long avatarByteSize) {
    this.avatarByteSize = avatarByteSize;
  }

  public String getAccountStatus() {
    return accountStatus;
  }

  public void setAccountStatus(String accountStatus) {
    this.accountStatus = accountStatus;
  }

  public OffsetDateTime getDeletionRequestedAt() {
    return deletionRequestedAt;
  }

  public void setDeletionRequestedAt(OffsetDateTime deletionRequestedAt) {
    this.deletionRequestedAt = deletionRequestedAt;
  }

  public OffsetDateTime getDeletionScheduledAt() {
    return deletionScheduledAt;
  }

  public void setDeletionScheduledAt(OffsetDateTime deletionScheduledAt) {
    this.deletionScheduledAt = deletionScheduledAt;
  }

  public OffsetDateTime getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(OffsetDateTime deletedAt) {
    this.deletedAt = deletedAt;
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
}
