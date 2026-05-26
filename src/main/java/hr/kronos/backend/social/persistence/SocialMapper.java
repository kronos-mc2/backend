package hr.kronos.backend.social.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SocialMapper {
  List<FriendRow> findFriends();

  List<FriendRow> findFriendsForUser(@Param("userId") String userId);

  List<FriendRow> findShareableFriendsForEvent(
      @Param("userId") String userId,
      @Param("creatorUserId") String creatorUserId);

  FriendRequestRow findFriendRequest(@Param("id") String id);

  FriendRequestRow findFriendRequestBetween(@Param("leftUserId") String leftUserId, @Param("rightUserId") String rightUserId);

  int insertFriendRequest(
      @Param("id") String id,
      @Param("requesterUserId") String requesterUserId,
      @Param("recipientUserId") String recipientUserId,
      @Param("chatRoomId") String chatRoomId);

  int updateFriendRequestStatus(@Param("id") String id, @Param("status") String status);
}
