package hr.kronos.backend.auth.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
@SuppressWarnings("java:S107")
public interface AuthMapper {
  UserRow findByEmail(@Param("email") String email);

  UserRow findById(@Param("id") String id);

  UserRow findBySocialIdentity(
      @Param("provider") String provider,
      @Param("providerSubject") String providerSubject);

  UserDeletionRecoveryRow findDeletionRecoveryByEmail(@Param("email") String email);

  UserDeletionRecoveryRow findDeletionRecoveryByUserId(@Param("userId") String userId);

  int insert(UserRow user);

  int insertSocialIdentity(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("provider") String provider,
      @Param("providerSubject") String providerSubject,
      @Param("email") String email);

  int updateSocialIdentityEmail(
      @Param("provider") String provider,
      @Param("providerSubject") String providerSubject,
      @Param("email") String email);

  int updateSocialIdentitiesEmailForUser(@Param("userId") String userId, @Param("email") String email);

  int updateName(@Param("id") String id, @Param("fullName") String fullName);

  int updateProfile(
      @Param("id") String id,
      @Param("fullName") String fullName,
      @Param("bio") String bio,
      @Param("avatarUrl") String avatarUrl);

  int updateProfileAvatar(
      @Param("id") String id,
      @Param("avatarUrl") String avatarUrl,
      @Param("avatarStorageKey") String avatarStorageKey,
      @Param("avatarBucketName") String avatarBucketName,
      @Param("avatarContentType") String avatarContentType,
      @Param("avatarByteSize") Long avatarByteSize);

  int updatePassword(@Param("id") String id, @Param("passwordHash") String passwordHash);

  int insertDeletionRecovery(
      @Param("userId") String userId,
      @Param("originalEmail") String originalEmail,
      @Param("originalFullName") String originalFullName,
      @Param("originalBio") String originalBio,
      @Param("originalAvatarUrl") String originalAvatarUrl,
      @Param("originalPasswordHash") String originalPasswordHash,
      @Param("originalAuthProvider") String originalAuthProvider,
      @Param("scheduledAt") java.time.OffsetDateTime scheduledAt);

  int markDeletionRequested(
      @Param("id") String id,
      @Param("anonymizedEmail") String anonymizedEmail,
      @Param("scheduledAt") java.time.OffsetDateTime scheduledAt);

  int anonymizeSocialIdentitiesForDeletion(
      @Param("userId") String userId,
      @Param("anonymizedEmail") String anonymizedEmail);

  int deletePersonalStateForDeletion(@Param("userId") String userId);

  int restoreUserFromDeletion(
      @Param("id") String id,
      @Param("email") String email,
      @Param("fullName") String fullName,
      @Param("bio") String bio,
      @Param("avatarUrl") String avatarUrl,
      @Param("passwordHash") String passwordHash,
      @Param("authProvider") String authProvider);

  int deleteDeletionRecovery(@Param("userId") String userId);

  int scrubExpiredSocialIdentities();

  int markExpiredDeletionsFinal();

  int deleteExpiredDeletionRecoveries();
}
