package hr.kronos.backend.messages.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MessageMapper {
  List<ConversationRow> findConversations();

  ConversationRow findConversationById(@Param("id") String id);

  int updateConversationSharePreview(
      @Param("id") String id,
      @Param("lastMessageHr") String lastMessageHr,
      @Param("lastMessageEn") String lastMessageEn);

  List<ChatRoomRow> findRoomsForUser(@Param("userId") String userId, @Param("query") String query);

  ChatRoomRow findRoomForUser(@Param("roomId") String roomId, @Param("userId") String userId);

  ChatRoomRow findEventRoomForUser(@Param("eventId") String eventId, @Param("userId") String userId);

  ChatRoomRow findDirectRoomForUsers(@Param("userId") String userId, @Param("memberUserId") String memberUserId);

  List<ChatMessageRow> findMessagesForRoom(@Param("roomId") String roomId, @Param("userId") String userId);

  ChatMessageRow findMessageById(@Param("messageId") String messageId, @Param("userId") String userId);

  List<ChatMemberRow> findMembersForRoom(@Param("roomId") String roomId);

  ChatMemberRow findMember(@Param("roomId") String roomId, @Param("userId") String userId);

  List<ChatPersonRow> searchPeople(@Param("userId") String userId, @Param("query") String query);

  int insertRoom(ChatRoomRow room);

  int insertMember(@Param("roomId") String roomId, @Param("userId") String userId, @Param("role") String role);

  int updateRoomAdminOnly(@Param("roomId") String roomId, @Param("adminOnly") boolean adminOnly);

  int touchRoom(@Param("roomId") String roomId);

  int insertMessage(ChatMessageRow message);

  int markRoomRead(@Param("roomId") String roomId, @Param("userId") String userId, @Param("messageId") String messageId);

  PollRow findPollById(@Param("pollId") String pollId);

  List<PollOptionRow> findPollOptions(@Param("pollId") String pollId, @Param("userId") String userId);

  int countPollVotes(@Param("pollId") String pollId);

  int insertPoll(PollRow poll);

  int insertPollOption(PollOptionRow option);

  int deletePollVotesForUser(@Param("pollId") String pollId, @Param("userId") String userId);

  int insertPollVote(@Param("pollId") String pollId, @Param("optionId") String optionId, @Param("userId") String userId);
}
