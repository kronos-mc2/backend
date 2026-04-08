package hr.kronos.backend.social.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SocialMapper {
  List<FriendRow> findFriends();
}
