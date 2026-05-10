package hr.kronos.backend.auth.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthMapper {
  UserRow findByEmail(@Param("email") String email);

  UserRow findById(@Param("id") String id);

  int insert(UserRow user);

  int updateName(@Param("id") String id, @Param("fullName") String fullName);

  int updateProfile(
      @Param("id") String id,
      @Param("fullName") String fullName,
      @Param("bio") String bio,
      @Param("avatarUrl") String avatarUrl);
}
