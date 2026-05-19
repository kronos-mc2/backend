package hr.kronos.backend.auth.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {
  UserRow findByEmail(@Param("email") String email);

  UserRow findById(@Param("id") String id);

  UserRow findBySocialIdentity(
      @Param("provider") String provider,
      @Param("providerSubject") String providerSubject);

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

  int updateName(@Param("id") String id, @Param("fullName") String fullName);

  int updateProfile(
      @Param("id") String id,
      @Param("fullName") String fullName,
      @Param("bio") String bio,
      @Param("avatarUrl") String avatarUrl);
}
